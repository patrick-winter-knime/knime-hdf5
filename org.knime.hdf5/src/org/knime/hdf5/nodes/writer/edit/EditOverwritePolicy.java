package org.knime.hdf5.nodes.writer.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum EditOverwritePolicy {
	
    /**
     * Do nothing. Throw an error if this case happens.
     */
    NONE("none"),

    /**
     * Abort if a TreeNodeEdit exists.
     */
    ABORT("abort"),
    
    /**
     * Overwrite old TreeNodeEdit.
     */
    OVERWRITE("overwrite"),
	
    /**
     * Rename the new TreeNodeEdit.
     */
    RENAME("rename"),
	
    /**
     * Add all children of the new TreeNodeEdit to the old TreeNodeEdit.
     */
    INTEGRATE("integrate");
	private static final Map<String, EditOverwritePolicy> LOOKUP = new HashMap<>();

	static {
		for (EditOverwritePolicy policy : EditOverwritePolicy.values()) {
			LOOKUP.put(policy.getName(), policy);
		}
	}

	private String m_name;
	
	private EditOverwritePolicy(String name) {
		m_name = name;
	}
	
	String getName() {
		return m_name;
	}
	
	public static EditOverwritePolicy get(String name) {
		return LOOKUP.get(name);
	}
	
	static EditOverwritePolicy[] getAvailableValuesForEdit(TreeNodeEdit edit) {
		List<EditOverwritePolicy> values = new ArrayList<>();
		
		if (edit instanceof GroupNodeEdit || edit instanceof DataSetNodeEdit || edit instanceof AttributeNodeEdit) {
			values.add(NONE);
			values.add(ABORT);
			values.add(OVERWRITE);
			if (edit.getEditAction().isCreateOrCopyAction()) {
				values.add(RENAME);
			}
			if (edit instanceof GroupNodeEdit || edit instanceof DataSetNodeEdit) {
				values.add(INTEGRATE);
			}
		}
		
		return values.toArray(new EditOverwritePolicy[values.size()]);
	}
	
	public static EditOverwritePolicy[] getAvailableValuesForFile() {
		return new EditOverwritePolicy[] { INTEGRATE, OVERWRITE, RENAME };
	}
	
	public static String[] getNames(EditOverwritePolicy[] policies) {
		String[] names = new String[policies.length];
		
		for (int i = 0; i < policies.length; i++) {
			names[i] = policies[i].getName();
		}
		
		return names;
	}
}