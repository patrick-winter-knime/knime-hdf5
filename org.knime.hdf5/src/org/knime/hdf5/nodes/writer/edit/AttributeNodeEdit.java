package org.knime.hdf5.nodes.writer.edit;

import java.awt.Dimension;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.hdf5.lib.Hdf5Attribute;
import org.knime.hdf5.lib.Hdf5File;
import org.knime.hdf5.lib.Hdf5TreeElement;
import org.knime.hdf5.lib.types.Hdf5HdfDataType.Endian;
import org.knime.hdf5.lib.types.Hdf5HdfDataType.HdfDataType;
import org.knime.hdf5.lib.types.Hdf5KnimeDataType;
import org.knime.hdf5.nodes.writer.edit.EditDataType.DataTypeChooser;
import org.knime.hdf5.nodes.writer.edit.EditDataType.Rounding;

public class AttributeNodeEdit extends TreeNodeEdit {
	
	private HdfDataType m_inputType;
	
	private List<HdfDataType> m_possibleOutputTypes;
	
	private EditDataType m_editDataType = new EditDataType();
	
	private int m_totalStringLength;

	private boolean m_compoundAsArrayPossible;
	
	private boolean m_compoundAsArrayUsed;
	
	private int m_compoundItemStringLength;
	
	private boolean m_overwrite;
	
	public AttributeNodeEdit(TreeNodeEdit parent, FlowVariable var) {
		this(parent, var.getName(), var.getName().replaceAll("\\\\/", "/"),
				HdfDataType.getHdfDataType(var.getType()), EditAction.CREATE);
		updatePropertiesFromFlowVariable(var);
	}

	private AttributeNodeEdit(TreeNodeEdit parent, AttributeNodeEdit copyAttribute) {
		this(parent, copyAttribute.getInputPathFromFileWithName(), copyAttribute.getName(), copyAttribute.getInputType(),
				copyAttribute.getEditAction() == EditAction.CREATE ? EditAction.CREATE : EditAction.COPY);
		copyAdditionalPropertiesFrom(copyAttribute);
		if (getEditAction() == EditAction.COPY) {
			copyAttribute.addIncompleteCopy(this);
		}
	}

	public AttributeNodeEdit(TreeNodeEdit parent, Hdf5Attribute<?> attribute) {
		this(parent, attribute.getPathFromFile() + attribute.getName().replaceAll("/", "\\\\/"), attribute.getName(),
				attribute.getType().getHdfType().getType(), EditAction.NO_ACTION);
		
		if (attribute.getType().isHdfType(HdfDataType.STRING)) {
			m_totalStringLength = (int) attribute.getType().getHdfType().getStringLength();
		} else {
			Object[] values = attribute.getValue() == null ? attribute.read() : attribute.getValue();
			for (Object value : values) {
				int newStringLength = value.toString().length();
				m_totalStringLength = newStringLength > m_totalStringLength ? newStringLength : m_totalStringLength;
			}
		}
		m_editDataType.setValues(m_inputType, attribute.getType().getHdfType().getEndian(), Rounding.DOWN, false, m_totalStringLength);
		m_possibleOutputTypes = HdfDataType.getConvertibleTypes(attribute);
		
		setHdfObject(attribute);
	}

	private AttributeNodeEdit(TreeNodeEdit parent, String inputPathFromFileWithName, String name, HdfDataType inputType, EditAction editAction) {
		super(inputPathFromFileWithName, parent.getOutputPathFromFileWithName(), name, editAction);
		setTreeNodeMenu(new AttributeNodeMenu());
		m_inputType = inputType;
		if (parent instanceof GroupNodeEdit) {
			((GroupNodeEdit) parent).addAttributeNodeEdit(this);
			
		} else if (parent instanceof DataSetNodeEdit) {
			((DataSetNodeEdit) parent).addAttributeNodeEdit(this);
			
		} else {
			throw new IllegalArgumentException("Error for \"" + getOutputPathFromFileWithName()
					+ "\": AttributeNodeEdits can only exist in GroupNodeEdits or DataSetNodeEdits");
		}
	}
	
	AttributeNodeEdit(GroupNodeEdit parent, String inputPathFromFileWithName, String name, HdfDataType inputType, EditAction editAction) {
		super(inputPathFromFileWithName, parent.getOutputPathFromFileWithName(), name, editAction);
		setTreeNodeMenu(new AttributeNodeMenu());
		m_inputType = inputType;
		// TODO test if this really works
		m_possibleOutputTypes = m_inputType.getPossiblyConvertibleHdfTypes();
		parent.addAttributeNodeEdit(this);
	}
	
	AttributeNodeEdit(DataSetNodeEdit parent, String inputPathFromFileWithName, String name, HdfDataType inputType, EditAction editAction) {
		super(inputPathFromFileWithName, parent.getOutputPathFromFileWithName(), name, editAction);
		setTreeNodeMenu(new AttributeNodeMenu());
		m_inputType = inputType;
		// TODO test if this really works
		m_possibleOutputTypes = m_inputType.getPossiblyConvertibleHdfTypes();
		parent.addAttributeNodeEdit(this);
	}
	
	public AttributeNodeEdit copyAttributeEditTo(TreeNodeEdit parent) {
		return new AttributeNodeEdit(parent, this);
	}
	
	private void updatePropertiesFromFlowVariable(FlowVariable var) {
		// TODO maybe work directly with HdfDataType for inputType
		Hdf5KnimeDataType knimeType = Hdf5KnimeDataType.getKnimeDataType(var.getType());
		Object[] values = Hdf5Attribute.getFlowVariableValues(var);
		knimeType = Hdf5KnimeDataType.getKnimeDataType(values);
		m_inputType = knimeType.getEquivalentHdfType();
		m_possibleOutputTypes = HdfDataType.getConvertibleTypes(var);
		m_totalStringLength = var.getValueAsString().length();
		
		for (Object value : values) {
			int newStringLength = value.toString().length();
			m_compoundItemStringLength = newStringLength > m_compoundItemStringLength ? newStringLength : m_compoundItemStringLength;
		}
		m_compoundAsArrayPossible = m_compoundItemStringLength != m_totalStringLength;
		m_compoundAsArrayUsed = m_compoundAsArrayPossible;
		
		m_editDataType.setValues(m_inputType, Endian.LITTLE_ENDIAN, Rounding.DOWN, false, m_compoundItemStringLength);
	}
	
	public HdfDataType getInputType() {
		return m_inputType;
	}

	private HdfDataType[] getPossibleOutputTypes() {
		return m_possibleOutputTypes.toArray(new HdfDataType[0]);
	}

	public EditDataType getEditDataType() {
		return m_editDataType;
	}
	
	private int getTotalStringLength() {
		return m_totalStringLength;
	}
	
	private boolean isCompoundAsArrayPossible() {
		return m_compoundAsArrayPossible;
	}
	
	private void setCompoundAsArrayPossible(boolean compoundAsArrayPossible) {
		m_compoundAsArrayPossible = compoundAsArrayPossible;
	}

	public boolean isCompoundAsArrayUsed() {
		return m_compoundAsArrayUsed;
	}
	
	private void setCompoundAsArrayUsed(boolean compoundAsArrayUsed) {
		m_compoundAsArrayUsed = compoundAsArrayUsed;
	}

	public int getCompoundItemStringLength() {
		return m_compoundItemStringLength;
	}

	private void setCompoundItemStringLength(int compoundItemStringLength) {
		m_compoundItemStringLength = compoundItemStringLength;
	}

	public boolean isOverwrite() {
		return m_overwrite;
	}

	private void setOverwrite(boolean overwrite) {
		m_overwrite = overwrite;
	}

	private boolean havePropertiesChanged() {
		boolean propertiesChanged = true;
		
		if (getInputPathFromFileWithName() != null) {
			try {
				Hdf5Attribute<?> copyAttribute = ((Hdf5File) getRoot().getHdfObject()).getAttributeByPath(getInputPathFromFileWithName());
				
				propertiesChanged = m_inputType != m_editDataType.getOutputType()
						|| copyAttribute.getType().getHdfType().getEndian() != m_editDataType.getEndian()
						|| m_totalStringLength != m_editDataType.getStringLength();
				
			} catch (IOException ioe) {
				NodeLogger.getLogger(getClass()).error(ioe.getMessage(), ioe);
			}
		}
		
		return propertiesChanged;
	}
	
	@Override
	protected void copyAdditionalPropertiesFrom(TreeNodeEdit copyEdit) {
		if (copyEdit instanceof AttributeNodeEdit) {
			AttributeNodeEdit copyAttributeEdit = (AttributeNodeEdit) copyEdit;
			m_editDataType.setValues(copyAttributeEdit.getEditDataType());
			m_totalStringLength = copyAttributeEdit.getTotalStringLength();
			m_possibleOutputTypes = Arrays.asList(copyAttributeEdit.getPossibleOutputTypes());
			m_compoundAsArrayPossible = copyAttributeEdit.isCompoundAsArrayPossible();
			m_compoundAsArrayUsed = copyAttributeEdit.isCompoundAsArrayUsed();
			m_compoundItemStringLength = copyAttributeEdit.getCompoundItemStringLength();
			m_overwrite = copyAttributeEdit.isOverwrite();
		}
	}
	
	@Override
	public String getToolTipText() {
		return "(" + m_editDataType.getOutputType().toString() + ") " + super.getToolTipText();
	}
	
	@Override
	protected TreeNodeEdit[] getAllChildren() {
		return new TreeNodeEdit[0];
	}

	@Override
	protected void removeFromParent() {
    	TreeNodeEdit parentEdit = getParent();
		if (parentEdit instanceof DataSetNodeEdit) {
			((DataSetNodeEdit) parentEdit).removeAttributeNodeEdit(this);
		} else if (parentEdit instanceof GroupNodeEdit) {
			((GroupNodeEdit) parentEdit).removeAttributeNodeEdit(this);	
		}
		setParent(null);
	}

	@Override
	public void saveSettingsTo(NodeSettingsWO settings) {
		super.saveSettingsTo(settings);

		settings.addInt(SettingsKey.INPUT_TYPE.getKey(), m_inputType.getTypeId());
		m_editDataType.saveSettingsTo(settings);
		settings.addBoolean(SettingsKey.COMPOUND_AS_ARRAY_POSSIBLE.getKey(), m_compoundAsArrayPossible);
		settings.addBoolean(SettingsKey.COMPOUND_AS_ARRAY_USED.getKey(), m_compoundAsArrayUsed);
		settings.addInt(SettingsKey.COMPOUND_ITEM_STRING_LENGTH.getKey(), m_compoundItemStringLength);
		settings.addBoolean(SettingsKey.OVERWRITE.getKey(), m_overwrite);
	}

	@Override
	protected void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		try {
	        if (!getEditAction().isCreateOrCopyAction()) {
	        	Hdf5TreeElement parent = (Hdf5TreeElement) getParent().getHdfObject();
	        	if (parent != null) {
		        	setHdfObject(parent.getAttribute(Hdf5Attribute.getPathAndName(getInputPathFromFileWithName())[1]));
	        	}
	        }
		} catch (IOException ioe) {
			// nothing to do here: edit will be invalid anyway
		}
		
		m_editDataType.loadSettingsFrom(settings);
		setCompoundAsArrayPossible(settings.getBoolean(SettingsKey.COMPOUND_AS_ARRAY_POSSIBLE.getKey()));
		setCompoundAsArrayUsed(settings.getBoolean(SettingsKey.COMPOUND_AS_ARRAY_USED.getKey()));
		setCompoundItemStringLength(settings.getInt(SettingsKey.COMPOUND_ITEM_STRING_LENGTH.getKey()));
		setOverwrite(settings.getBoolean(SettingsKey.OVERWRITE.getKey()));
	}
	
	@Override
	protected InvalidCause validateEditInternal(BufferedDataTable inputTable) {
		return null;
	}

	@Override
	protected boolean isInConflict(TreeNodeEdit edit) {
		return edit instanceof AttributeNodeEdit && !edit.equals(this) && edit.getName().equals(getName())
				&& getEditAction() != EditAction.DELETE && edit.getEditAction() != EditAction.DELETE;
	}

	@Override
	protected boolean createAction(BufferedDataTable inputTable, Map<String, FlowVariable> flowVariables, boolean saveColumnProperties) {
		Hdf5TreeElement parent = (Hdf5TreeElement) getParent().getHdfObject();
		if (!parent.isFile()) {
			parent.open();
		}
		
		Hdf5Attribute<?> newAttribute = null;
		FlowVariable var = flowVariables.get(getInputPathFromFileWithName());
		try {
			newAttribute = parent.createAndWriteAttribute(this, var);
		} catch (IOException ioe) {
			NodeLogger.getLogger(getClass()).error(ioe.getMessage(), ioe);
		}
		setHdfObject(newAttribute);
		
		return newAttribute != null;
	}

	@Override
	protected boolean copyAction() {
		try {
			Hdf5Attribute<?> copyAttribute = ((Hdf5File) getRoot().getHdfObject()).getAttributeByPath(getInputPathFromFileWithName());
			
			Hdf5TreeElement parent = (Hdf5TreeElement) getParent().getHdfObject();
			if (!parent.isFile()) {
				parent.open();
			}
			
			Hdf5Attribute<?> newAttribute = null;
			try {
				newAttribute = parent.createAndWriteAttribute(this, copyAttribute);
			} catch (IOException ioe) {
				NodeLogger.getLogger(getClass()).error(ioe.getMessage(), ioe);
			}
			setHdfObject(newAttribute);
			
			return newAttribute != null;
			
		} catch (IOException ioe) {
			NodeLogger.getLogger(getClass()).error(ioe.getMessage(), ioe);
		}

		return false;
	}

	@Override
	protected boolean deleteAction() {
		Hdf5TreeElement parent = (Hdf5TreeElement) getParent().getHdfObject();
		if (!parent.isFile()) {
			parent.open();
		}
		
		boolean success = parent.deleteAttribute(Hdf5Attribute.getPathAndName(getInputPathFromFileWithName())[1]) >= 0;
		if (success) {
			setHdfObject((Hdf5Attribute<?>) null);
		}
		
		return success;
	}

	@Override
	protected boolean modifyAction() {
		boolean success = true;
		
		Hdf5TreeElement parent = (Hdf5TreeElement) getParent().getHdfObject();
		if (!parent.isFile()) {
			parent.open();
		}
		String[] pathAndName = Hdf5Attribute.getPathAndName(getInputPathFromFileWithName());
		String oldName = pathAndName[1];
		
		if (!havePropertiesChanged() && getOutputPathFromFile().equals(pathAndName[0])) {
			if (!oldName.equals(getName())) {
				success = parent.renameAttribute(oldName, getName());
			}
		} else {
			Hdf5Attribute<?> tempAttribute = null;
			try {
				tempAttribute = parent.copyAttribute(TreeNodeEdit.getUniqueName(parent.loadAttributeNames(), getName() + "(1)"), parent.getAttribute(oldName));
			} catch (IOException ioe) {
				NodeLogger.getLogger(getClass()).error(ioe.getMessage(), ioe);
			}
			success = tempAttribute != null;
			success &= parent.deleteAttribute(oldName) >= 0;
			
			try {
				Hdf5Attribute<?> newAttribute = parent.createAndWriteAttribute(this, tempAttribute);
				setHdfObject(newAttribute);
				setInputPathFromFileWithName(newAttribute.getPathFromFileWithName());
				success &= newAttribute != null;
				
			} catch (IOException ioe) {
				success = false;
				try {
					Hdf5Attribute<?> changedNameAttribute = parent.copyAttribute(TreeNodeEdit.getUniqueName(parent.loadAttributeNames(), getName()), tempAttribute);
					setHdfObject(changedNameAttribute);
					setInputPathFromFileWithName(changedNameAttribute.getPathFromFileWithName());
					
				} catch (IOException ioe2) {
					NodeLogger.getLogger(getClass()).error(ioe2.getMessage(), ioe2);
				}
			}

			success &= parent.deleteAttribute(tempAttribute.getName()) >= 0;
		}
		
		return success;
	}
	
	public class AttributeNodeMenu extends TreeNodeMenu {

		private static final long serialVersionUID = -6418394582185524L;
    	
		private AttributeNodeMenu() {
			super(true, false, true);
    	}
		
		@Override
		protected PropertiesDialog getPropertiesDialog() {
			return new AttributePropertiesDialog();
		}

		@Override
		protected void onDelete() {
			AttributeNodeEdit edit = AttributeNodeEdit.this;
			TreeNodeEdit parentOfVisible = edit.getParent();
        	edit.setDeletion(edit.getEditAction() != EditAction.DELETE);
            parentOfVisible.reloadTreeWithEditVisible(true);
		}
		
		private class AttributePropertiesDialog extends PropertiesDialog {
	    	
			private static final long serialVersionUID = 9201153080744087510L;
	    	
			private JTextField m_nameField = new JTextField(15);
			private DataTypeChooser m_dataTypeChooser = m_editDataType.new DataTypeChooser();
			private JCheckBox m_compoundAsArrayField = new JCheckBox();
			private JRadioButton m_overwriteNo = new JRadioButton("no");
			private JRadioButton m_overwriteYes = new JRadioButton("yes");
			
			private AttributePropertiesDialog() {
				super(AttributeNodeMenu.this, "Attribute properties");
				setMinimumSize(new Dimension(450, 300));

				addProperty("Name: ", m_nameField);
				m_dataTypeChooser.addToPropertiesDialog(this);
				
				if (m_compoundAsArrayPossible) {
					m_compoundAsArrayField.addChangeListener(new ChangeListener() {
						
						@Override
						public void stateChanged(ChangeEvent e) {
							boolean selected = m_compoundAsArrayField.isSelected();
							m_dataTypeChooser.setOnlyStringSelectable(!selected,
									selected ? m_compoundItemStringLength : m_totalStringLength);
						}
					});
				} else {
					m_compoundAsArrayField.setEnabled(false);
				}
				addProperty("Use values from flowVariable array: ", m_compoundAsArrayField);

				JPanel overwriteField = new JPanel();
				ButtonGroup overwriteGroup = new ButtonGroup();
				overwriteField.add(m_overwriteNo);
				overwriteGroup.add(m_overwriteNo);
				overwriteField.add(m_overwriteYes);
				overwriteGroup.add(m_overwriteYes);
				addProperty("Overwrite: ", overwriteField);
			}
			
			@Override
			protected void loadFromEdit() {
				AttributeNodeEdit edit = AttributeNodeEdit.this;
				m_nameField.setText(edit.getName());
				m_dataTypeChooser.loadFromDataType(m_possibleOutputTypes, m_inputType == HdfDataType.FLOAT32);
				m_compoundAsArrayField.setSelected(edit.isCompoundAsArrayUsed());
				m_overwriteNo.setSelected(!edit.isOverwrite());
				m_overwriteYes.setSelected(edit.isOverwrite());
			}

			@Override
			protected void saveToEdit() {
				AttributeNodeEdit edit = AttributeNodeEdit.this;
				edit.setName(m_nameField.getText());
				m_dataTypeChooser.saveToDataType();
				edit.setCompoundAsArrayUsed(m_compoundAsArrayField.isSelected());
				edit.setOverwrite(m_overwriteYes.isSelected());
				edit.setEditAction(EditAction.MODIFY);

				edit.reloadTreeWithEditVisible();
			}
		}
    }
}
