package org.knime.hdf5.nodes.writer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeCreationContext;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;
import org.knime.hdf5.lib.Hdf5File;
import org.knime.hdf5.nodes.writer.edit.EditOverwritePolicy;
import org.knime.hdf5.nodes.writer.edit.FileNodeEdit;

/**
 * The {@link NodeModel} for the hdf writer in order to
 * export hdf files.
 */
public class HDF5WriterNodeModel extends NodeModel {

	private SettingsModelString m_filePathSettings;
	
	private SettingsModelString m_fileOverwritePolicySettings;
	
	private SettingsModelBoolean m_saveColumnPropertiesSettings;
	
	private EditTreeConfiguration m_editTreeConfig;
	
	protected HDF5WriterNodeModel() {
		super(new PortType[] { BufferedDataTable.TYPE_OPTIONAL }, new PortType[] {});
		m_filePathSettings = SettingsFactory.createFilePathSettings();
		m_fileOverwritePolicySettings = SettingsFactory.createFileOverwritePolicySettings();
		m_saveColumnPropertiesSettings = SettingsFactory.createSaveColumnPropertiesSettings();
		m_editTreeConfig = SettingsFactory.createEditTreeConfiguration();
	}
	
	protected HDF5WriterNodeModel(NodeCreationContext context) {
		this();
		m_filePathSettings.setStringValue(context.getUrl().getPath());
	}

	@Override
	protected BufferedDataTable[] execute(BufferedDataTable[] inData, ExecutionContext exec) throws Exception {
		checkForErrors(m_editTreeConfig, inData[0], true);

		boolean success = false;
		FileNodeEdit fileEdit = m_editTreeConfig.getFileNodeEdit();
		try {
			success = fileEdit.doAction(inData[0], getAvailableFlowVariables(), m_saveColumnPropertiesSettings.getBooleanValue(), exec);
			
		} finally {
			NodeLogger.getLogger(getClass()).debug("Success: " + success);
			NodeLogger.getLogger(getClass()).debug("States of all edits after execution:\n" + fileEdit.getSummaryOfEditStates(false));
			
			if (success) {
				try {
					fileEdit.deleteAllBackups();
					
				} catch (Exception e) {
					NodeLogger.getLogger(getClass()).warn("Deletion of backups failed: " + e.getMessage(), e);
				}
			} else {
				boolean rollbackSuccess = false;
				try {
					if (!success) {
						rollbackSuccess = fileEdit.doRollbackAction();
					}
				} catch (Exception e) {
					NodeLogger.getLogger(getClass()).error("Rollback failed: " + e.getMessage(), e);
				
				} finally {
					NodeLogger.getLogger(getClass()).debug("Success of rollback: " + rollbackSuccess);
					NodeLogger.getLogger(getClass()).debug("States of all edits after rollback:\n" + fileEdit.getSummaryOfEditStates(true));
				}
			}
			
			if (fileEdit.getHdfObject() != null) {
				((Hdf5File) fileEdit.getHdfObject()).close();
			}
		}
		
		return null;
	}
	
	@Override
	protected DataTableSpec[] configure(DataTableSpec[] inSpecs) throws InvalidSettingsException {
		checkForErrors(m_editTreeConfig);
		return null;
    }
	
	/**
	 * Checks for errors in the {@code editTreeConfig} which is the case if
	 * elements of its {@linkplain FileNodeEdit}s are invalid.
	 * 
	 * @param editTreeConfig the config to be checked
	 * @throws InvalidSettingsException if the config is not valid or could not
	 * 	be checked
	 */
	static void checkForErrors(EditTreeConfiguration editTreeConfig) throws InvalidSettingsException {
		checkForErrors(editTreeConfig, null, false);
	}
	
	/**
	 * Checks for errors in the {@code editTreeConfig} which is the case if
	 * elements of its {@linkplain FileNodeEdit}s are invalid.
	 * If the table {@code inputTable} is not null, it will also be used to
	 * validate the data types and row sizes of the dataSet edits that should
	 * create new hdf dataSets.
	 * 
	 * @param editTreeConfig the config to be checked
	 * @param inputTable the knime input table to use for validation
	 * @throws InvalidSettingsException if the config is not valid or could not
	 * 	be checked
	 */
	private static void checkForErrors(EditTreeConfiguration editTreeConfig,
			BufferedDataTable inputTable, boolean lastValidationBeforeExecution) throws InvalidSettingsException {
		FileNodeEdit fileEdit = editTreeConfig.getFileNodeEdit();
		if (fileEdit == null) {
			throw new InvalidSettingsException("No file selected");
		}
		
		Hdf5File file = null;
		try {
			FileNodeEdit oldFileEdit = null;
			if (!fileEdit.isOverwriteHdfFile() && Hdf5File.existsHdf5File(fileEdit.getFilePath())) {
				file = Hdf5File.openFile(fileEdit.getFilePath(), Hdf5File.READ_ONLY_ACCESS);
				oldFileEdit = new FileNodeEdit(file);
				oldFileEdit.loadChildrenOfHdfObject();
				
			} else {
				oldFileEdit = new FileNodeEdit(fileEdit.getFilePath(), fileEdit.isOverwriteHdfFile());
			}
			
			boolean valid = lastValidationBeforeExecution ? oldFileEdit.finalIntegrateAndValidate(fileEdit, inputTable) : oldFileEdit.integrateAndValidate(fileEdit);
			if (!valid) {
				throw new InvalidSettingsException("The configuration for file \"" + oldFileEdit.getFilePath()
						+ "\" is not valid:\n" + oldFileEdit.getInvalidCauseMessages(fileEdit));
			}
		} catch (IOException ioe) {
			throw new InvalidSettingsException("Could not check configuration: " + ioe.getMessage(), ioe.getCause());
			
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (IOException ioe) {
					NodeLogger.getLogger(HDF5WriterNodeModel.class).error(ioe.getMessage(), ioe);
				}
			}
		}
	}
	
	/**
	 * Get the file path out of the {@code urlPath}.
	 * 
	 * @param urlPath the knime url path
	 * @param mustExist if the file must exist
	 * @return the file path
	 * @throws InvalidSettingsException if the file path is invalid or the
	 * 	respective file does not exist
	 */
	static String getFilePathFromUrlPath(String urlPath, boolean mustExist) throws InvalidSettingsException {
		if (urlPath == null || urlPath.trim().isEmpty()) {
			throw new InvalidSettingsException("No file selected");
		}
        
        try {
        	Path filePath = FileUtil.resolveToPath(FileUtil.toURL(urlPath));
        	if (mustExist || filePath.toFile().exists()) {
            	CheckUtils.checkSourceFile(filePath.toString());
            } else {
            	CheckUtils.checkDestinationDirectory(filePath.getParent().toString());
            }
            
            return filePath.toString();
            
        } catch (InvalidSettingsException | InvalidPathException | IOException | URISyntaxException | NullPointerException isipiousnpe) {
        	throw new InvalidSettingsException("Incorrect file path/url: " + isipiousnpe.getMessage(), isipiousnpe);
        }
	}

	/**
	 * Not needed here.
	 */
	@Override
	protected void loadInternals(File nodeInternDir, ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	/**
	 * Not needed here.
	 */
	@Override
	protected void saveInternals(File nodeInternDir, ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	@Override
	protected void saveSettingsTo(NodeSettingsWO settings) {
		m_filePathSettings.saveSettingsTo(settings);
		m_fileOverwritePolicySettings.saveSettingsTo(settings);
		m_saveColumnPropertiesSettings.saveSettingsTo(settings);
		m_editTreeConfig.saveConfiguration(settings);
	}

	@Override
	protected void validateSettings(NodeSettingsRO settings) throws InvalidSettingsException {
		SettingsModelString filePathSettings = SettingsFactory.createFilePathSettings();
		filePathSettings.validateSettings(settings);
		filePathSettings.loadSettingsFrom(settings);

		SettingsModelString fileOverWritePolicySettings = SettingsFactory.createFileOverwritePolicySettings();
		fileOverWritePolicySettings.validateSettings(settings);
		fileOverWritePolicySettings.loadSettingsFrom(settings);
		EditOverwritePolicy policy = EditOverwritePolicy.get(fileOverWritePolicySettings.getStringValue());
		
		SettingsModelBoolean saveColumnPropertiesSettings = SettingsFactory.createSaveColumnPropertiesSettings();
		saveColumnPropertiesSettings.validateSettings(settings);
		saveColumnPropertiesSettings.loadSettingsFrom(settings);
		
		EditTreeConfiguration editTreeConfig = SettingsFactory.createEditTreeConfiguration();
		editTreeConfig.loadConfiguration(settings, null, policy);
		checkForErrors(editTreeConfig);
	}

	@Override
	protected void loadValidatedSettingsFrom(NodeSettingsRO settings) throws InvalidSettingsException {
		m_filePathSettings.loadSettingsFrom(settings);
		m_fileOverwritePolicySettings.loadSettingsFrom(settings);
		m_saveColumnPropertiesSettings.loadSettingsFrom(settings);
		
		EditTreeConfiguration editTreeConfig = SettingsFactory.createEditTreeConfiguration();
		editTreeConfig.loadConfiguration(settings, null, EditOverwritePolicy.get(m_fileOverwritePolicySettings.getStringValue()));
		m_editTreeConfig = editTreeConfig;
	}

	@Override
	protected void reset() {
		try {
			m_editTreeConfig.updateConfiguration(m_filePathSettings.getStringValue(),
					EditOverwritePolicy.get(m_fileOverwritePolicySettings.getStringValue()));
		} catch (IOException | InvalidSettingsException ioise) {
			NodeLogger.getLogger(getClass()).error("Reset failed: " + ioise.getMessage(), ioise);
		}
	}
}
