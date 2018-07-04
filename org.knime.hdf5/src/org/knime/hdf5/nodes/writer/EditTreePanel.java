package org.knime.hdf5.nodes.writer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;

import javax.activation.UnsupportedDataTypeException;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.hdf5.lib.Hdf5Attribute;
import org.knime.hdf5.lib.Hdf5DataSet;
import org.knime.hdf5.lib.Hdf5File;
import org.knime.hdf5.lib.Hdf5Group;
import org.knime.hdf5.lib.Hdf5TreeElement;
import org.knime.hdf5.nodes.writer.SettingsFactory.SpecInfo;
import org.knime.hdf5.nodes.writer.edit.AttributeNodeEdit;
import org.knime.hdf5.nodes.writer.edit.ColumnNodeEdit;
import org.knime.hdf5.nodes.writer.edit.DataSetNodeEdit;
import org.knime.hdf5.nodes.writer.edit.FileNodeEdit;
import org.knime.hdf5.nodes.writer.edit.GroupNodeEdit;
import org.knime.hdf5.nodes.writer.edit.TreeNodeEdit;

public class EditTreePanel extends JPanel {

	private static final long serialVersionUID = -9129152385004241047L;

	private EditTreeConfiguration m_editTreeConfig = new EditTreeConfiguration("temp");
	
	private final JTree m_tree = new JTree(new DefaultMutableTreeNode("(null)"));

	public EditTreePanel() {
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createTitledBorder("File:"));
		
		final JScrollPane jsp = new JScrollPane(m_tree);
		add(jsp, BorderLayout.CENTER);
		
    	m_tree.setCellRenderer(new DefaultTreeCellRenderer() {

			private static final long serialVersionUID = -2424225988962935310L;

			private final Icon columnIcon = loadIcon(EditTreePanel.class, "/icon/column.png");
			private final Icon attributeIcon = loadIcon(EditTreePanel.class, "/icon/attribute.png");
			private final Icon dataSetIcon = loadIcon(EditTreePanel.class, "/icon/dataSet.png");
			private final Icon fileIcon = loadIcon(EditTreePanel.class, "/icon/file.png");
			private final Icon groupIcon = loadIcon(EditTreePanel.class, "/icon/group.png");
			private final Icon newColumnIcon = loadIcon(EditTreePanel.class, "/icon/column_new.png");
			private final Icon newAttributeIcon = loadIcon(EditTreePanel.class, "/icon/attribute_new.png");
			private final Icon newDataSetIcon = loadIcon(EditTreePanel.class, "/icon/dataSet_new.png");
			private final Icon newFileIcon = loadIcon(EditTreePanel.class, "/icon/file_new.png");
			private final Icon newGroupIcon = loadIcon(EditTreePanel.class, "/icon/group_new.png");
			private final Icon invalidAttributeIcon = loadIcon(EditTreePanel.class, "/icon/attribute_invalid.png");
			private final Icon invalidDataSetIcon = loadIcon(EditTreePanel.class, "/icon/dataSet_invalid.png");
			private final Icon invalidFileIcon = loadIcon(EditTreePanel.class, "/icon/file_invalid.png");
			private final Icon invalidGroupIcon = loadIcon(EditTreePanel.class, "/icon/group_invalid.png");
			
			private Icon loadIcon(
		            final Class<?> className, final String path) {
		        ImageIcon icon;
		        try {
		            ClassLoader loader = className.getClassLoader();
		            String packagePath =
		                className.getPackage().getName().replace('.', '/');
		            String correctedPath = path;
		            if (!path.startsWith("/")) {
		                correctedPath = "/" + path;
		            }
		            icon = new ImageIcon(
		                    loader.getResource(packagePath + correctedPath));
		        } catch (Exception e) {
		            NodeLogger.getLogger(EditTreePanel.class).debug(
		                    "Unable to load icon at path " + path, e);
		            icon = null;
		        }
		        return icon;
		    }
			
			@Override
			public Component getTreeCellRendererComponent(final JTree tree,
					final Object value, final boolean sel, final boolean expanded,
					final boolean leaf, final int row, final boolean hasfocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
						hasfocus);
				
				if (value instanceof DefaultMutableTreeNode) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
					Object nodeObject = node.getUserObject();

					String text = null;
					Icon icon = null;
					
					if (nodeObject instanceof DataColumnSpec) {
						text = ((DataColumnSpec) nodeObject).getName();
						icon = columnIcon;
						
					} else if (nodeObject instanceof Hdf5Attribute) {
						text = ((Hdf5Attribute<?>) nodeObject).getName();
						icon = attributeIcon;
					
					} else if (nodeObject instanceof Hdf5TreeElement) {
						Hdf5TreeElement treeElement = (Hdf5TreeElement) nodeObject;
						text = treeElement.getName();
						if (treeElement.isDataSet()) {
							icon = dataSetIcon;
						} else if (treeElement.isFile()) {
							icon = fileIcon;
						} else if (treeElement.isGroup()) {
							icon = groupIcon;
						}
					} else if (nodeObject instanceof TreeNodeEdit) {
						TreeNodeEdit edit = (TreeNodeEdit) nodeObject;
						text = edit.getName();
						if (edit instanceof ColumnNodeEdit) {
							icon = newColumnIcon;
							
						} else if (edit instanceof AttributeNodeEdit) {
							icon = edit.isValid() ? newAttributeIcon : invalidAttributeIcon;
							
						} else if (edit instanceof DataSetNodeEdit) {
							icon = edit.isValid() ? newDataSetIcon : invalidDataSetIcon;
							
						} else if (edit instanceof FileNodeEdit) {
							icon = edit.isValid() ? newFileIcon : invalidFileIcon;
							
						} else if (edit instanceof GroupNodeEdit) {
							icon = edit.isValid() ? newGroupIcon : invalidGroupIcon;
						}
					}

					setText(text);
					setIcon(icon);
				}
				
				return this;
			}
		});
		
    	m_tree.setDragEnabled(true);
    	m_tree.setTransferHandler(new TransferHandler() {

			private static final long serialVersionUID = -4233815652319877595L;
        	
			public boolean canImport(TransferHandler.TransferSupport info) {
				// we only import Strings
                JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();
                return info.isDataFlavorSupported(DataFlavor.stringFlavor) && dl.getPath() != null;
            }

			public boolean importData(TransferHandler.TransferSupport info) {
                if (!info.isDrop()) {
                    return false;
                }

                // Check for String flavor
                if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    return false;
                }
                
                JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();
                TreePath path = dl.getPath();
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) path.getLastPathComponent();
                
                // Get the string that is being dropped.
                Transferable t = info.getTransferable();
                List<?> data = null;
                try {
                	data = SpecInfo.get((String) t.getTransferData(DataFlavor.stringFlavor)).getSpecList();
                	if (data == null || data.isEmpty()) {
                		return false;
                	}
                } catch (UnsupportedFlavorException | IOException ufioe) {
                	return false;
                }
                
                Object parentObject = parent.getUserObject();
                if (parentObject instanceof DataColumnSpec || parentObject instanceof ColumnNodeEdit
                		|| parentObject instanceof Hdf5Attribute || parentObject instanceof AttributeNodeEdit) {
                	path = path.getParentPath();
                	parent = (DefaultMutableTreeNode) parent.getParent();
                	parentObject = parent.getUserObject();
                }
                
                if (data.get(0) instanceof DataColumnSpec) {
                	if (!(parentObject instanceof Hdf5DataSet<?> || parentObject instanceof DataSetNodeEdit)) {
                		String newName = "dataSet[" + parent.getChildCount() + "]";
                		DataSetNodeEdit newEdit = null;
                		if (parentObject instanceof GroupNodeEdit) {
                			GroupNodeEdit edit = (GroupNodeEdit) parentObject;
                    		newEdit = new DataSetNodeEdit(newName);
                    		edit.addDataSetNodeEdit(newEdit);
                    		
                		} else {
                    		newEdit = new DataSetNodeEdit(parent, newName);
                        	m_editTreeConfig.addDataSetNodeEdit(newEdit);
                		}
                    	newEdit.addEditToNode(parent);
                    	DefaultMutableTreeNode newChild = newEdit.getTreeNode();
                    	path = path.pathByAddingChild(newChild);
                    	parent = newChild;
                    	parentObject = newEdit;
                	}

                    // TODO include case to append dataSets
                	for (int i = 0; i < data.size(); i++) {
                		DataColumnSpec spec = (DataColumnSpec) data.get(i);
                		ColumnNodeEdit newEdit = null;
                		if (parentObject instanceof DataSetNodeEdit) {
                    		try {
                    			DataSetNodeEdit edit = (DataSetNodeEdit) parentObject;
								newEdit = new ColumnNodeEdit(spec);
								edit.addColumnNodeEdit(newEdit);
								
							} catch (UnsupportedDataTypeException udte) {
								// TODO exception
							}
                		}
                    	newEdit.addEditToNode(parent);
                	}
                } else if (data.get(0) instanceof FlowVariable) {
                    for (int i = 0; i < data.size(); i++) {
                    	FlowVariable var = (FlowVariable) data.get(i);
                    	
                    	AttributeNodeEdit newEdit = null;
                    	if (parentObject instanceof GroupNodeEdit) {
                    		GroupNodeEdit edit = (GroupNodeEdit) parentObject;
                    		newEdit = new AttributeNodeEdit(var);
                    		edit.addAttributeNodeEdit(newEdit);
                    		
                		} else if (parentObject instanceof DataSetNodeEdit) {
                			DataSetNodeEdit edit = (DataSetNodeEdit) parentObject;
                    		newEdit = new AttributeNodeEdit(var);
                    		edit.addAttributeNodeEdit(newEdit);
                    		
                		} else {
                    		newEdit = new AttributeNodeEdit(parent, var);
                        	m_editTreeConfig.addAttributeNodeEdit(newEdit);
                		}
                    	
                    	newEdit.addEditToNode(parent);
                	}
                }
				
				((DefaultTreeModel) (m_tree.getModel())).reload();
				m_tree.makeVisible(path.pathByAddingChild(parent.getFirstChild()));
                
                return true;
            }
        });
    	m_tree.setDropMode(DropMode.ON_OR_INSERT);

    	m_tree.addMouseListener(new MouseAdapter() {

    		@Override
    		public void mousePressed(MouseEvent e) {
    			if (e.isPopupTrigger()) {
    				createMenu(e);
    			}
    		}
    		
    		@Override
    		public void mouseReleased(MouseEvent e) {
    			if (e.isPopupTrigger()) {
    				createMenu(e);
    			}
    		}
    		
    		// TODO check if this method does not get called twice
    		private void createMenu(MouseEvent e) {
    			TreePath path = m_tree.getPathForLocation(e.getX(), e.getY());
				if (path != null) {
					m_tree.setSelectionPath(path);
    				DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    				Object userObject = node.getUserObject();
    				if (userObject instanceof DataColumnSpec) {
    					ColumnNodeEdit.COLUMN_MENU.initMenu(m_tree, m_editTreeConfig, node);
    					ColumnNodeEdit.COLUMN_MENU.show(e.getComponent(), e.getX(), e.getY());
    					
    				} else if (userObject instanceof Hdf5Attribute) {
    					// TODO
    					
    				} else if (userObject instanceof Hdf5DataSet) {
    					// TODO
    					
    				} else if (userObject instanceof Hdf5Group) {
        				GroupNodeEdit.GROUP_MENU.initMenu(m_tree, m_editTreeConfig, node);
    					GroupNodeEdit.GROUP_MENU.show(e.getComponent(), e.getX(), e.getY());
    					
    				} else if (userObject instanceof ColumnNodeEdit) {
    					ColumnNodeEdit.COLUMN_EDIT_MENU.initMenu(m_tree, m_editTreeConfig, node);
    					ColumnNodeEdit.COLUMN_EDIT_MENU.show(e.getComponent(), e.getX(), e.getY());
    					
    				} else if (userObject instanceof AttributeNodeEdit) {
    					AttributeNodeEdit.AttributeNodeMenu menu = ((AttributeNodeEdit) userObject).getAttributeEditMenu();
    					menu.initMenu(m_tree, m_editTreeConfig, node);
    					menu.show(e.getComponent(), e.getX(), e.getY());
    					
    				} else if (userObject instanceof DataSetNodeEdit) {
    					DataSetNodeEdit.DataSetNodeMenu menu = ((DataSetNodeEdit) userObject).getDataSetEditMenu();
    					menu.initMenu(m_tree, m_editTreeConfig, node);
    					menu.show(e.getComponent(), e.getX(), e.getY());
    					
    				} else if (userObject instanceof GroupNodeEdit) {
    					GroupNodeEdit.GROUP_EDIT_MENU.initMenu(m_tree, m_editTreeConfig, node);
    					GroupNodeEdit.GROUP_EDIT_MENU.show(e.getComponent(), e.getX(), e.getY());
    				}
				}
    		}
		});
	}
	
	JTree getTree() {
		return m_tree;
	}
	
	void prepareForFile(FileNodeEdit edit) {
		((DefaultTreeModel) m_tree.getModel()).setRoot(new DefaultMutableTreeNode(edit));
		m_editTreeConfig.setFileNodeEdit(edit);
	}

	void updateTreeWithFile(Hdf5File file) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(file);
		((DefaultTreeModel) m_tree.getModel()).setRoot(root);
		addChildrenToNode(root);
		if (root.getChildCount() > 0) {
			TreePath path = new TreePath(root.getPath());
			m_tree.makeVisible(path.pathByAddingChild(root.getFirstChild()));
		}
	}
    
    private void addChildrenToNode(DefaultMutableTreeNode parentNode) {
    	Object userObject = parentNode.getUserObject();
    	
    	if (userObject instanceof Hdf5TreeElement) {
        	Hdf5TreeElement parent = (Hdf5TreeElement) userObject;

        	if (parent.isGroup()) {
        		Hdf5Group parentGroup = (Hdf5Group) parent;
        		
            	try {
            		for (String groupName : parentGroup.loadGroupNames()) {
            			Hdf5Group group = parentGroup.getGroup(groupName);
            			DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(group);
            			parentNode.add(childNode);
            			addChildrenToNode(childNode);
            		}
            		for (String dataSetName : parentGroup.loadDataSetNames()) {
            			Hdf5DataSet<?> dataSet = parentGroup.getDataSet(dataSetName);
            			DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(dataSet);
            			parentNode.add(childNode);
            			addChildrenToNode(childNode);
            		}
            	} catch (IOException ioe) {
            		// TODO exception
            	}
        	} else if (parent.isDataSet()) {
        		Hdf5DataSet<?> parentDS = (Hdf5DataSet<?>) parent;
        		
        		// TODO what to do with 1 dimension?
        		if (parentDS.getDimensions().length == 2) {
        			for (int i = 0; i < parentDS.getDimensions()[1]; i++) {
        				try {
    						DataColumnSpec spec = new DataColumnSpecCreator("col" + (i + 1), parentDS.getType().getKnimeType().getColumnDataType()).createSpec();
    		    			DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(spec);
    		    			childNode.setAllowsChildren(false);
    	    				parentNode.add(childNode);
    	        			
    					} catch (UnsupportedDataTypeException udte) {
    						NodeLogger.getLogger("HDF5 Files").error(udte.getMessage(), udte);
    					}
        			}
        		}
        	}
        	
        	try {
        		for (String attributeName : parent.loadAttributeNames()) {
        			Hdf5Attribute<?> attribute = parent.getAttribute(attributeName);
        			DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(attribute);
        			parentNode.add(childNode);
        		}
        	} catch (IOException ioe) {
        		// TODO exception
        	}
    	}
    }

	void saveConfiguration(EditTreeConfiguration editTreeConfig) {
		if (m_editTreeConfig.getFileNodeEdit() != null) {
			editTreeConfig.setFileNodeEdit(m_editTreeConfig.getFileNodeEdit());
		}
		for (GroupNodeEdit edit : m_editTreeConfig.getGroupNodeEdits()) {
			editTreeConfig.addGroupNodeEdit(edit);
		}
		for (DataSetNodeEdit edit : m_editTreeConfig.getDataSetNodeEdits()) {
			editTreeConfig.addDataSetNodeEdit(edit);
		}
		for (AttributeNodeEdit edit : m_editTreeConfig.getAttributeNodeEdits()) {
			editTreeConfig.addAttributeNodeEdit(edit);
		}
	}
	
	@SuppressWarnings("unchecked")
	void loadConfiguration(EditTreeConfiguration editTreeConfig) {
		if (editTreeConfig.getFileNodeEdit() != null) {
			editTreeConfig.getFileNodeEdit().setEditAsRoot((DefaultTreeModel) m_tree.getModel());;
		}
		
		for (GroupNodeEdit edit : editTreeConfig.getGroupNodeEdits()) {
			String[] pathFromFile = edit.getPathFromFile().split("/");
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) m_tree.getModel().getRoot();
			
			boolean found = true;
			for (String name : pathFromFile) {
				if (!name.isEmpty()) {
					Enumeration<DefaultMutableTreeNode> children = node.children();
					found = false;
					while (!found && children.hasMoreElements()) {
						node = children.nextElement();
						Object userObject = node.getUserObject();
						if (userObject instanceof Hdf5TreeElement) {
							found = name.equals(((Hdf5TreeElement) userObject).getName());
						} else if (userObject instanceof TreeNodeEdit) {
							found = name.equals(((TreeNodeEdit) userObject).getName());
						}
					}
				}
			}
			
			if (found) {
				edit.addEditToNode(node);
			}
		}
		
		for (DataSetNodeEdit edit : editTreeConfig.getDataSetNodeEdits()) {
			String[] pathFromFile = edit.getPathFromFile().split("/");
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) m_tree.getModel().getRoot();
			
			boolean found = true;
			for (String name : pathFromFile) {
				if (!name.isEmpty()) {
					Enumeration<DefaultMutableTreeNode> children = node.children();
					found = false;
					while (!found && children.hasMoreElements()) {
						node = children.nextElement();
						Object userObject = node.getUserObject();
						if (userObject instanceof Hdf5TreeElement) {
							found = name.equals(((Hdf5TreeElement) userObject).getName());
						} else if (userObject instanceof TreeNodeEdit) {
							found = name.equals(((TreeNodeEdit) userObject).getName());
						}
					}
				}
			}
			
			if (found) {
				edit.addEditToNode(node);
			}
		}
		
		for (AttributeNodeEdit edit : editTreeConfig.getAttributeNodeEdits()) {
			String[] pathFromFile = edit.getPathFromFile().split("/");
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) m_tree.getModel().getRoot();
			
			boolean found = true;
			for (String name : pathFromFile) {
				if (!name.isEmpty()) {
					Enumeration<DefaultMutableTreeNode> children = node.children();
					found = false;
					while (!found && children.hasMoreElements()) {
						node = children.nextElement();
						Object userObject = node.getUserObject();
						if (userObject instanceof Hdf5TreeElement) {
							found = name.equals(((Hdf5TreeElement) userObject).getName());
						} else if (userObject instanceof TreeNodeEdit) {
							found = name.equals(((TreeNodeEdit) userObject).getName());
						}
					}
				}
			}
			
			if (found) {
				edit.addEditToNode(node);
			}
		}
		
		m_editTreeConfig = editTreeConfig;
	}
}
