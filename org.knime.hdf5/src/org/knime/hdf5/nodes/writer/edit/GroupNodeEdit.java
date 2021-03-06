package org.knime.hdf5.nodes.writer.edit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.activation.UnsupportedDataTypeException;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.hdf5.lib.Hdf5Attribute;
import org.knime.hdf5.lib.Hdf5DataSet;
import org.knime.hdf5.lib.Hdf5Group;
import org.knime.hdf5.lib.types.Hdf5HdfDataType.HdfDataType;

/**
 * Class for edits on groups in an hdf file. The respective hdf
 * source is an {@linkplain Hdf5Group}.
 */
public class GroupNodeEdit extends TreeNodeEdit {
	
	private final List<GroupNodeEdit> m_groupEdits = new ArrayList<>();

	private final List<DataSetNodeEdit> m_dataSetEdits = new ArrayList<>();
	
	private final List<UnsupportedObjectNodeEdit> m_unsupportedObjectEdits = new ArrayList<>();
	
	private final List<AttributeNodeEdit> m_attributeEdits = new ArrayList<>();

	/**
	 * Creates a new group edit to CREATE a new group with the {@code name}
	 * in the hdf group of {@code parent}.
	 * 
	 * @param parent the parent of this edit
	 * @param name the output name of this edit
	 */
	public GroupNodeEdit(GroupNodeEdit parent, String name) {
		this(parent, null, name, EditOverwritePolicy.NONE, EditAction.CREATE);
	}
	
	/**
	 * Copies the group edit {@code copyGroup} to {@code parent} with all
	 * properties, but without its child edits.
	 * <br>
	 * <br>
	 * If {@code needsCopySource} is {@code true}, the action of this edit
	 * will be set to COPY, except if {@code copyGroup}'s edit action is CREATE.
	 * In all other cases, the action of this edit is the same as of {@code copyGroup}.
	 * 
	 * @param parent the parent of this edit
	 * @param copyGroup the group edit to copy from
	 * @param needsCopySource if the {@code copyGroup} is needed to execute a COPY action
	 * 	with this edit later
	 */
	private GroupNodeEdit(GroupNodeEdit parent, GroupNodeEdit copyGroup, boolean needsCopySource) {
		this(parent, copyGroup.getInputPathFromFileWithName(), copyGroup.getName(), copyGroup.getEditOverwritePolicy(),
				needsCopySource ? (copyGroup.getEditAction() == EditAction.CREATE ? EditAction.CREATE : EditAction.COPY) : copyGroup.getEditAction());
		if (needsCopySource && getEditAction() == EditAction.COPY) {
			setCopyEdit(copyGroup);
		}
	}
	
	/**
	 * Initializes a new group edit for the input hdf {@code group}.
	 * The edit action is set to NO_ACTION.
	 * 
	 * @param parent the parent of this edit
	 * @param group the hdf group for this edit
	 * @throws IllegalArgumentException if the parent of {@code group} and the
	 * 	hdf object of {@code parent} are not the same hdf group
	 */
	public GroupNodeEdit(GroupNodeEdit parent, Hdf5Group group) throws IllegalArgumentException {
		this(parent, group.getPathFromFileWithName(), group.getName(), EditOverwritePolicy.NONE, EditAction.NO_ACTION);
		
		if (group.getParent() != parent.getHdfObject()) {
			throw new IllegalArgumentException("Hdf group cannot be added to this edit.");
		}
			
		setHdfObject(group);
	}
	
	/**
	 * Initializes a new group edit with all core settings.
	 * 
	 * @param parent the parent of this edit
	 * @param inputPathFromFileWithName the path of this edit's hdf group
	 * @param name the output name of this edit
	 * @param editOverwritePolicy the overwrite policy for this edit
	 * @param editAction the action of this edit
	 */
	protected GroupNodeEdit(GroupNodeEdit parent, String inputPathFromFileWithName, String name, EditOverwritePolicy editOverwritePolicy, EditAction editAction) {
		super(inputPathFromFileWithName, parent != null && !(parent instanceof FileNodeEdit)
				? parent.getOutputPathFromFileWithName() : "", name, editOverwritePolicy, editAction);
		setTreeNodeMenu(new GroupNodeMenu());
		if (parent != null) {
			parent.addGroupNodeEdit(this);
		}
	}
	
	/**
	 * Copies this edit to {@code parent} with all descendants. Does the same as
	 * {@code copyGroupEditTo(parent, true)}.
	 * 
	 * @param parent the destination of the new copy
	 * @return the new copy
	 * @throws IllegalArgumentException	if {@code parent} is already a
	 * 	descendant of this edit
	 */
	public GroupNodeEdit copyGroupEditTo(GroupNodeEdit parent) throws IllegalArgumentException {
		return copyGroupEditTo(parent, true);
	}
	
	/**
	 * Copies this edit to {@code parent} with all descendants.
	 * 
	 * @param parent the destination of the new copy
	 * @param needsCopySource if the information about this edit is needed for
	 * 	the new edit
	 * @return the new copy
	 * @throws IllegalArgumentException	if {@code parent} is already a
	 * 	descendant of this edit
	 */
	GroupNodeEdit copyGroupEditTo(GroupNodeEdit parent, boolean needsCopySource) throws IllegalArgumentException {
		if (isEditDescendantOf(parent)) {
			throw new IllegalArgumentException("Cannot copy this group to a descendant");
		}
		
		GroupNodeEdit newGroupEdit = new GroupNodeEdit(parent, this, needsCopySource);
		newGroupEdit.addEditToParentNodeIfPossible();
		
		for (GroupNodeEdit groupEdit : getGroupNodeEdits()) {
			groupEdit.copyGroupEditTo(newGroupEdit, needsCopySource);
		}
		
		for (DataSetNodeEdit dataSetEdit : getDataSetNodeEdits()) {
			dataSetEdit.copyDataSetEditTo(newGroupEdit, needsCopySource);
		}
		
		for (AttributeNodeEdit attributeEdit : getAttributeNodeEdits()) {
			attributeEdit.copyAttributeEditTo(newGroupEdit, needsCopySource);
		}
		
		return newGroupEdit;
	}

	/**
	 * @return the children of this edit which are group edits
	 */
	public GroupNodeEdit[] getGroupNodeEdits() {
		return m_groupEdits.toArray(new GroupNodeEdit[] {});
	}

	/**
	 * @return the children of this edit which are dataSet edits
	 */
	public DataSetNodeEdit[] getDataSetNodeEdits() {
		return m_dataSetEdits.toArray(new DataSetNodeEdit[] {});
	}

	/**
	 * @return the children of this edit which are attribute edits
	 */
	public AttributeNodeEdit[] getAttributeNodeEdits() {
		return m_attributeEdits.toArray(new AttributeNodeEdit[] {});
	}
	
	/**
	 * Get the child group edit with the specified input path within the hdf file
	 * of this edit's root or {@code null} if none exists.
	 * 
	 * @param inputPathFromFileWithName the input path from file
	 * @return the searched child group edit
	 */
	GroupNodeEdit getGroupNodeEdit(String inputPathFromFileWithName) {
		if (inputPathFromFileWithName != null) {
			for (GroupNodeEdit groupEdit : m_groupEdits) {
				if (inputPathFromFileWithName.equals(groupEdit.getInputPathFromFileWithName())
						&& !groupEdit.getEditAction().isCreateOrCopyAction()) {
					return groupEdit;
				}
			}
		}
		return null;
	}

	/**
	 * Get the child dataSet edit with the specified input path within the hdf file
	 * of this edit's root or {@code null} if none exists.
	 * 
	 * @param inputPathFromFileWithName the input path from file
	 * @return the searched child dataSet edit
	 */
	DataSetNodeEdit getDataSetNodeEdit(String inputPathFromFileWithName) {
		if (inputPathFromFileWithName != null) {
			for (DataSetNodeEdit dataSetEdit : m_dataSetEdits) {
				if (inputPathFromFileWithName.equals(dataSetEdit.getInputPathFromFileWithName())
						&& !dataSetEdit.getEditAction().isCreateOrCopyAction()) {
					return dataSetEdit;
				}
			}
		}
		return null;
	}

	/**
	 * For {@code editAction == EditAction.CREATE}, get the child attribute edit
	 * (with CREATE action) for the flowVariable with the name
	 * {@code inputPathFromFileWithName} or {@code null} if none exists.
	 * <br>
	 * <br>
	 * For {@code editAction == EditAction.COPY}, get the child attribute edit
	 * with COPY action with the specified input path within the hdf file of
	 * this edit's root or {@code null} if none exists.
	 * <br>
	 * <br>
	 * For other {@code editAction}s, get the child attribute edit
	 * without COPY action with the specified input path within the hdf file of
	 * this edit's root or {@code null} if none exists.
	 * 
	 * @param inputPathFromFileWithName the input path from file
	 * @param editAction the input path from file
	 * @return the searched child attribute edit
	 */
	AttributeNodeEdit getAttributeNodeEdit(String inputPathFromFileWithName, EditAction editAction) {
		if (inputPathFromFileWithName != null) {
			for (AttributeNodeEdit attributeEdit : m_attributeEdits) {
				if (inputPathFromFileWithName.equals(attributeEdit.getInputPathFromFileWithName())
						&& (editAction == EditAction.CREATE) == (attributeEdit.getEditAction() == EditAction.CREATE)
						&& (editAction == EditAction.COPY) == (attributeEdit.getEditAction() == EditAction.COPY)) {
					return attributeEdit;
				}
			}
		}
		return null;
	}

	private void addGroupNodeEdit(GroupNodeEdit edit) {
		m_groupEdits.add(edit);
		edit.setParent(this);
	}

	void addDataSetNodeEdit(DataSetNodeEdit edit) {
		m_dataSetEdits.add(edit);
		edit.setParent(this);
	}
	
	void addUnsupportedObjectNodeEdit(UnsupportedObjectNodeEdit edit) {
		m_unsupportedObjectEdits.add(edit);
		edit.setParent(this);
	}
	
	void addAttributeNodeEdit(AttributeNodeEdit edit) {
		m_attributeEdits.add(edit);
		edit.setParent(this);
	}

	private void removeGroupNodeEdit(GroupNodeEdit edit) {
		m_groupEdits.remove(edit);
		if (getTreeNode() != null) {
			getTreeNode().remove(edit.getTreeNode());
		}
		edit.setParent(null);
	}

	void removeDataSetNodeEdit(DataSetNodeEdit edit) {
		m_dataSetEdits.remove(edit);
		if (getTreeNode() != null) {
			getTreeNode().remove(edit.getTreeNode());
		}
		edit.setParent(null);
	}
	
	void removeUnsupportedObjectNodeEdit(UnsupportedObjectNodeEdit edit) {
		m_unsupportedObjectEdits.remove(edit);
		if (getTreeNode() != null) {
			getTreeNode().remove(edit.getTreeNode());
		}
		edit.setParent(null);
	}
	
	void removeAttributeNodeEdit(AttributeNodeEdit edit) {
		m_attributeEdits.remove(edit);
		if (getTreeNode() != null) {
			getTreeNode().remove(edit.getTreeNode());
		}
		edit.setParent(null);
	}
	
	@Override
	protected boolean havePropertiesChanged(Object hdfSource) {
		return false;
	}
	
	@Override
	protected void copyAdditionalPropertiesFrom(TreeNodeEdit copyEdit) {
		// nothing to do here
	}
	
	@Override
	protected long getProgressToDoInEdit() {
		return getEditAction() != EditAction.NO_ACTION && getEditAction() != EditAction.MODIFY_CHILDREN_ONLY && getEditState() != EditState.SUCCESS ? 147L : 0L;
	}
	
	@Override
	protected TreeNodeEdit[] getAllChildren() {
		List<TreeNodeEdit> children = new ArrayList<>();
		
		children.addAll(m_groupEdits);
		children.addAll(m_dataSetEdits);
		children.addAll(m_unsupportedObjectEdits);
		children.addAll(m_attributeEdits);
		
		return children.toArray(new TreeNodeEdit[0]);
	}
	
	/**
	 * Integrates the children (and descendants) of {@code copyEdit}
	 * (not those with {@code editAction == NO_ACTION}) in this group edit. The
	 * properties of this group edit will stay the same. The properties
	 * of this edit's children will be overwritten for those with an
	 * action (not for those with {@code editAction == MODIFY_CHILDREN_ONLY}).
	 * 
	 * @param copyEdit the group edit to be integrated
	 */
	void integrate(GroupNodeEdit copyEdit) {
		for (GroupNodeEdit copyGroupEdit : copyEdit.getGroupNodeEdits()) {
			if (copyGroupEdit.getEditAction() != EditAction.NO_ACTION) {
				// see if the group edit already exists
				GroupNodeEdit groupEdit = getGroupNodeEdit(copyGroupEdit.getInputPathFromFileWithName());
				if (groupEdit == null) {
					// see if a dataSet edit already exists to add child attributes into
					DataSetNodeEdit dataSetEdit = getDataSetNodeEdit(copyGroupEdit.getInputPathFromFileWithName());
					if (dataSetEdit != null) {
						dataSetEdit.integrateAttributeEdits(copyGroupEdit);
						continue;
					}
				}

				// copy the properties or the whole group edit into here
				boolean isCreateOrCopyAction = copyGroupEdit.getEditAction().isCreateOrCopyAction();
				if (groupEdit != null && !isCreateOrCopyAction) {
					if (copyGroupEdit.getEditAction() != EditAction.MODIFY_CHILDREN_ONLY) {
						groupEdit.copyPropertiesFrom(copyGroupEdit);
					}
					groupEdit.integrate(copyGroupEdit);
					
				} else {
					copyGroupEdit.copyGroupEditTo(this, false);
				}
			}
		}
		
		for (DataSetNodeEdit copyDataSetEdit : copyEdit.getDataSetNodeEdits()) {
			if (copyDataSetEdit.getEditAction() != EditAction.NO_ACTION) {
				// see if the dataSet edit already exists
				DataSetNodeEdit dataSetEdit = getDataSetNodeEdit(copyDataSetEdit.getInputPathFromFileWithName());
				if (dataSetEdit == null) {
					// see if a group edit already exists to add child attributes into
					GroupNodeEdit groupEdit = getGroupNodeEdit(copyDataSetEdit.getInputPathFromFileWithName());
					if (groupEdit != null) {
						groupEdit.integrateAttributeEdits(copyDataSetEdit);
						continue;
					}
				}

				// copy the properties or the whole dataSet edit into here
				boolean isCreateOrCopyAction = copyDataSetEdit.getEditAction().isCreateOrCopyAction();
				if (dataSetEdit != null && !isCreateOrCopyAction) {
					if (copyDataSetEdit.getEditAction() != EditAction.MODIFY_CHILDREN_ONLY) {
						dataSetEdit.copyPropertiesFrom(copyDataSetEdit);
					}
					dataSetEdit.integrate(copyDataSetEdit, true);
					
				} else {
					copyDataSetEdit.copyDataSetEditTo(this, false);
				}
			}
		}
		
		integrateAttributeEdits(copyEdit);
	}
	
	@Override
	protected void removeFromParent() {
		if (this instanceof FileNodeEdit) {
			throw new IllegalStateException("Cannot remove a FileNodeEdit from a parent.");
		}
		((GroupNodeEdit) getParent()).removeGroupNodeEdit(this);
	}
	
	@Override
	public void saveSettingsTo(NodeSettingsWO settings) {
		super.saveSettingsTo(settings);
		
        NodeSettingsWO groupSettings = settings.addNodeSettings(SettingsKey.GROUPS.getKey());
        NodeSettingsWO dataSetSettings = settings.addNodeSettings(SettingsKey.DATA_SETS.getKey());
        NodeSettingsWO attributeSettings = settings.addNodeSettings(SettingsKey.ATTRIBUTES.getKey());
        
        for (GroupNodeEdit edit : m_groupEdits) {
        	if (edit.getEditAction() != EditAction.NO_ACTION) {
    	        NodeSettingsWO editSettings = groupSettings.addNodeSettings("" + edit.hashCode());
    			edit.saveSettingsTo(editSettings);
        	}
		}
		
		for (DataSetNodeEdit edit : m_dataSetEdits) {
        	if (edit.getEditAction() != EditAction.NO_ACTION) {
		        NodeSettingsWO editSettings = dataSetSettings.addNodeSettings("" + edit.hashCode());
				edit.saveSettingsTo(editSettings);
        	}
		}
		
		for (AttributeNodeEdit edit : m_attributeEdits) {
        	if (edit.getEditAction() != EditAction.NO_ACTION) {
		        NodeSettingsWO editSettings = attributeSettings.addNodeSettings("" + edit.hashCode());
				edit.saveSettingsTo(editSettings);
        	}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		NodeSettingsRO groupSettings = settings.getNodeSettings(SettingsKey.GROUPS.getKey());
        Enumeration<NodeSettingsRO> groupEnum = groupSettings.children();
        while (groupEnum.hasMoreElements()) {
        	NodeSettingsRO editSettings = groupEnum.nextElement();
        	GroupNodeEdit edit = new GroupNodeEdit(this, editSettings.getString(SettingsKey.INPUT_PATH_FROM_FILE_WITH_NAME.getKey()), 
        			editSettings.getString(SettingsKey.NAME.getKey()), EditOverwritePolicy.get(editSettings.getString(SettingsKey.EDIT_OVERWRITE_POLICY.getKey())),
        			EditAction.get(editSettings.getString(SettingsKey.EDIT_ACTION.getKey())));
            edit.loadSettingsFrom(editSettings);
        }
        
        NodeSettingsRO dataSetSettings = settings.getNodeSettings(SettingsKey.DATA_SETS.getKey());
        Enumeration<NodeSettingsRO> dataSetEnum = dataSetSettings.children();
        while (dataSetEnum.hasMoreElements()) {
        	NodeSettingsRO editSettings = dataSetEnum.nextElement();
        	DataSetNodeEdit edit = new DataSetNodeEdit(this, editSettings.getString(SettingsKey.INPUT_PATH_FROM_FILE_WITH_NAME.getKey()),
        			editSettings.getString(SettingsKey.NAME.getKey()), EditOverwritePolicy.get(editSettings.getString(SettingsKey.EDIT_OVERWRITE_POLICY.getKey())),
        			EditAction.get(editSettings.getString(SettingsKey.EDIT_ACTION.getKey())));
    		edit.loadSettingsFrom(editSettings);
        }
        
        NodeSettingsRO attributeSettings = settings.getNodeSettings(SettingsKey.ATTRIBUTES.getKey());
        Enumeration<NodeSettingsRO> attributeEnum = attributeSettings.children();
        while (attributeEnum.hasMoreElements()) {
        	NodeSettingsRO editSettings = attributeEnum.nextElement();
			AttributeNodeEdit edit = new AttributeNodeEdit(this, editSettings.getString(SettingsKey.INPUT_PATH_FROM_FILE_WITH_NAME.getKey()),
					editSettings.getString(SettingsKey.NAME.getKey()), EditOverwritePolicy.get(editSettings.getString(SettingsKey.EDIT_OVERWRITE_POLICY.getKey())),
					HdfDataType.get(editSettings.getInt(SettingsKey.INPUT_TYPE.getKey())), EditAction.get(editSettings.getString(SettingsKey.EDIT_ACTION.getKey())));
			edit.loadSettingsFrom(editSettings);
        }
	}

	/**
	 * Loads the children/descendants of the hdf group for which this edit is
	 * for and adds those as newly initialized child edits to this edit.
	 * 
	 * @throws IOException if the hdf group is not loaded or not open or
	 * 	an error in the hdf library occurred
	 */
	void loadChildrenOfHdfObject() throws IOException {
		Hdf5Group group = (Hdf5Group) getHdfObject();
		
    	try {
    		List<String> otherObjectNames = new ArrayList<>(Arrays.asList(group.loadObjectNames()));
    		
    		for (String groupName : group.loadGroupNames()) {
    			Hdf5Group child = group.getGroup(groupName);
    			GroupNodeEdit childEdit = new GroupNodeEdit(this, child);
    			childEdit.addEditToParentNodeIfPossible();
    			childEdit.loadChildrenOfHdfObject();
    			
    			otherObjectNames.remove(groupName);
    		}

    		for (String dataSetName : group.loadDataSetNames()) {
    			DataSetNodeEdit childEdit = null;
    			try {
        			Hdf5DataSet<?> child = group.getDataSet(dataSetName);
        			if (child.getDimensions().length == 0) {
            			childEdit = new DataSetNodeEdit(this, child.getName(), "Scalar dataSet");
            			childEdit.addEditToParentNodeIfPossible();
            			
        			} else if (child.getDimensions().length <= 2) {
            			childEdit = new DataSetNodeEdit(this, child);
            			childEdit.addEditToParentNodeIfPossible();
            			childEdit.loadChildrenOfHdfObject();
            			
        			} else {
            			childEdit = new DataSetNodeEdit(this, child.getName(), "More than 2 dimensions");
            			childEdit.addEditToParentNodeIfPossible();
        			}
    			} catch (UnsupportedDataTypeException udte) {
    				// for unsupported dataSets
        			childEdit = new DataSetNodeEdit(this, dataSetName, "Unsupported data type");
        			childEdit.addEditToParentNodeIfPossible();
    			}
    			otherObjectNames.remove(dataSetName);
    		}
    		
    		for (String otherObjectName : otherObjectNames) {
    			UnsupportedObjectNodeEdit childEdit = new UnsupportedObjectNodeEdit(this, otherObjectName);
    			childEdit.addEditToParentNodeIfPossible();
    		}
    		
    		for (String attributeName : group.loadAttributeNames()) {
    			AttributeNodeEdit childEdit = null;
    			try {
        			Hdf5Attribute<?> child = group.getAttribute(attributeName);
        			childEdit = new AttributeNodeEdit(this, child);
        			childEdit.addEditToParentNodeIfPossible();
        			
    			} catch (UnsupportedDataTypeException udte) {
    				// for unsupported attributes
        			childEdit = new AttributeNodeEdit(this, attributeName, "Unsupported data type");
        			childEdit.addEditToParentNodeIfPossible();
    			}
    		}
    	} catch (NullPointerException npe) {
    		throw new IOException(npe.getMessage(), npe);
    	}
	}
	
	@Override
	protected InvalidCause validateEditInternal() {
		return getName().contains("/") || getName().isEmpty() ? InvalidCause.NAME_CHARS :
			getName().startsWith(BACKUP_PREFIX) && !getOutputPathFromFileWithName(true).equals(getInputPathFromFileWithName())
					? InvalidCause.NAME_BACKUP_PREFIX : null;
	}

	@Override
	protected boolean isInConflict(TreeNodeEdit edit) {
		boolean conflictPossible = !(edit instanceof ColumnNodeEdit) && !(edit instanceof AttributeNodeEdit) && this != edit;
		boolean inConflict = conflictPossible && getName().equals(edit.getName()) && getEditAction() != EditAction.DELETE && edit.getEditAction() != EditAction.DELETE;

		return inConflict ? !avoidsOverwritePolicyNameConflict(edit) : conflictPossible && willBeNameConflictWithIgnoredEdit(edit);
	}

	@Override
	protected void createAction(Map<String, FlowVariable> flowVariables, ExecutionContext exec, long totalProgressToDo) throws IOException {
		try {
			setHdfObject((Hdf5Group) null);
			setHdfObject(((Hdf5Group) getOpenedHdfSourceOfParent()).createGroup(getName()));
		} finally {
			setEditSuccess(getHdfObject() != null);
		}
	}
	
	@Override
	protected void copyAction(ExecutionContext exec, long totalProgressToDo) throws IOException {
		createAction(null, exec, totalProgressToDo);
	}

	@Override
	protected void deleteAction() throws IOException {
		try {
			Hdf5Group group = (Hdf5Group) getHdfObject();
			if (((Hdf5Group) getOpenedHdfSourceOfParent()).deleteObject(group.getName())) {
				setHdfObject((Hdf5Group) null);
			}
		} finally {
			setEditSuccess(getHdfObject() == null);
		}
	}

	@Override
	protected void modifyAction(ExecutionContext exec, long totalProgressToDo) throws IOException {
		try {
			Hdf5Group oldGroup = (Hdf5Group) getHdfSource();
			setHdfObject((Hdf5Group) null);
			if (!oldGroup.getName().equals(getName())) {
				if (oldGroup == getHdfBackup()) {
					setHdfObject(((Hdf5Group) getOpenedHdfSourceOfParent()).copyObject(oldGroup, getName()));
				} else {
					setHdfObject(((Hdf5Group) getOpenedHdfSourceOfParent()).moveObject(oldGroup, getName()));
				}
			}
		} finally {
			setEditSuccess(getHdfObject() != null);
		}
	}
	
	/**
	 * The class for the {@linkplain JPopupMenu} which can be accessed through
	 * a right mouse click on this group edit.
	 */
	public class GroupNodeMenu extends TreeNodeMenu {

    	private static final long serialVersionUID = -7709804406752499090L;
    	
		private GroupNodeMenu() {
			super(!(GroupNodeEdit.this instanceof FileNodeEdit), true, !(GroupNodeEdit.this instanceof FileNodeEdit));
    	}

		/**
		 * Returns the dialog to modify the properties of this group edit.
		 * 
		 * @return the properties dialog
		 */
		@Override
		protected PropertiesDialog getPropertiesDialog() {
			return new GroupPropertiesDialog();
		}

		/**
		 * Creates a new group edit as child of this edit to create a new hdf
		 * group on execution.
		 */
		@Override
		protected void onCreateGroup() {
			GroupNodeEdit edit = GroupNodeEdit.this;
			String newName = getUniqueName(edit, GroupNodeEdit.class, "group");
			GroupNodeEdit newEdit = new GroupNodeEdit(edit, newName);
            newEdit.addEditToParentNodeIfPossible();
            newEdit.reloadTreeWithEditVisible();
		}

		@Override
		protected void onDelete() {
			GroupNodeEdit edit = GroupNodeEdit.this;
			TreeNodeEdit parentOfVisible = edit.getParent();
        	edit.setDeletion(edit.getEditAction() != EditAction.DELETE);
            parentOfVisible.reloadTreeWithEditVisible(true);
		}
		
		/**
		 * A {@linkplain JDialog} to set all properties of this group edit.
		 */
		private class GroupPropertiesDialog extends PropertiesDialog {
	    	
	    	private static final long serialVersionUID = 1254593831386973543L;
	    	
			private final JTextField m_nameField = new JTextField(15);
			private final JComboBox<EditOverwritePolicy> m_overwriteField = new JComboBox<>(EditOverwritePolicy.getAvailablePoliciesForEdit(GroupNodeEdit.this));
	    	
			private GroupPropertiesDialog() {
				super(GroupNodeMenu.this, "Group properties");

				addProperty("Name: ", m_nameField);
				addProperty("Overwrite: ", m_overwriteField);
				
				pack();
			}
			
			@Override
			protected void loadFromEdit() {
				GroupNodeEdit edit = GroupNodeEdit.this;
				m_nameField.setText(edit.getName());
				m_overwriteField.setSelectedItem(edit.getEditOverwritePolicy());
			}

			@Override
			protected void saveToEdit() {
				GroupNodeEdit edit = GroupNodeEdit.this;
				edit.setName(m_nameField.getText());
				edit.setEditOverwritePolicy((EditOverwritePolicy) m_overwriteField.getSelectedItem());
				edit.setEditAction(EditAction.MODIFY);

				edit.reloadTreeWithEditVisible();
			}
		}
    }
	
	@Override
	public String toString() {
		return "{ input=" + getInputPathFromFileWithName() + ",output=" + getOutputPathFromFileWithName()
				+ ",action=" + getEditAction() + ",state=" + getEditState()
				+ ",overwrite=" + getEditOverwritePolicy() + ",valid=" + isValid() 
				+ ",group=" + getHdfObject() + ",backup=" + getHdfBackup()+ " }";
	}
}
