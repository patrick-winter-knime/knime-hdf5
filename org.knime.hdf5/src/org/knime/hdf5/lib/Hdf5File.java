package org.knime.hdf5.lib;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.knime.core.node.NodeLogger;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5FileInterfaceException;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

public class Hdf5File extends Hdf5Group {
	
	private static final List<Hdf5File> ALL_FILES = new LinkedList<>();
	
	private Hdf5File(final String filePath) {
		super(null, filePath, filePath.substring(filePath.lastIndexOf(File.separator) + 1), true);
		
		ALL_FILES.add(this);
        this.setPathFromFile("/");
		this.open();
	}
	
	/**
	 * Creating an instance creates a new file or, if there's already a file in this path, opens it. <br>
	 * If the File.separator is not '/', the part of the path after the last File.separator
	 * may not contain '/'.
	 * 
	 * @param filePath The path to the file from the src directory.
	 */
	public static Hdf5File createFile(final String filePath) {
		Iterator<Hdf5File> iter = ALL_FILES.iterator();
		while (iter.hasNext()) {
			Hdf5File file = iter.next();
			if (file.getFilePath().equals(filePath)) {
				file.open();
				return file;
			}
		}
		
		return new Hdf5File(filePath);
	}
	
	public void open() {
		try {
			if (!this.isOpen()) {
				this.setElementId(H5.H5Fopen(this.getFilePath(), HDF5Constants.H5F_ACC_RDWR,
						HDF5Constants.H5P_DEFAULT));
				NodeLogger.getLogger("HDF5 Files").info("File " + this.getName() + " opened: " + this.getElementId());
				this.setOpen(true);
			}
		} catch (HDF5FileInterfaceException e) {
	    	try {
				this.setElementId(H5.H5Fcreate(this.getFilePath(), HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT));
				NodeLogger.getLogger("HDF5 Files").info("File " + this.getName() + " created: " + this.getElementId());
                this.setOpen(true);
            } catch (Exception e2) {
				e2.printStackTrace();
			}
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	/**
	 * Just to see what objects of a file are still opened. <br>
	 * <br>
	 * <b>Types</b>:
	 * <ul>
	 *   <li> 1 - File </li>
	 *   <li> 2 - Group </li>
	 *   <li> 3 - DataType </li>
	 *   <li> 4 - DataSpace </li>
	 *   <li> 5 - DataSet </li>
	 *   <li> 6 - Attribute </li>
	 * </ul>
	 * 
	 * @return number of opened objects
	 */
	public long whatIsOpen() {
        long count = -1;
        long openedObjects = -1;
        long objectType = -1;
        long[] objects;
        String pathFromFile = "";
		
        try {
        	if (this.isOpen()) {
        		count = H5.H5Fget_obj_count(this.getElementId(), HDF5Constants.H5F_OBJ_ALL);
			} else {
				NodeLogger.getLogger("HDF5 Files").error("File " + this.getName() + " is not opened!",
						new IllegalStateException());
				return count;
			}
		} catch (HDF5LibraryException e1) {
			e1.printStackTrace();
		}

        if (count <= 0) {
        	return count;
        }

        System.out.println("\nObject(s) open: " + count);
        
        objects = new long[(int) count];

        try {
			openedObjects = H5.H5Fget_obj_ids(this.getElementId(), HDF5Constants.H5F_OBJ_ALL, count, objects);
		} catch (HDF5LibraryException | NullPointerException e) {
			e.printStackTrace();
		}

        System.out.println("Open objects:\n");

        for (int i = 0; i < openedObjects; i++) {
        	try {
        		objectType = H5.H5Iget_type(objects[i]);
			} catch (HDF5LibraryException e) {
				e.printStackTrace();
			}
            try {
				pathFromFile = H5.H5Iget_name(objects[i]);
			} catch (HDF5LibraryException e) {
				e.printStackTrace();
			}
            System.out.println(i + ": type " + objectType + ", name " + pathFromFile);
        }
         
        return openedObjects;
	}

	/**
	 * Closes the group and all elements in this group.
	 * 
	 */
	@Override
	public void close() {
		try {
            if (this.isOpen()) {
            	Iterator<Hdf5DataSet<?>> iterDss = this.getDataSets().iterator();
	    		while (iterDss.hasNext()) {
	    			iterDss.next().close();
	    		}

	    		Iterator<Hdf5Attribute<?>> iterAttrs = this.getAttributes().iterator();
	    		while (iterAttrs.hasNext()) {
	    			iterAttrs.next().close();
	    		}
	    		
	    		Iterator<Hdf5Group> iterGrps = this.getGroups().iterator();
	    		while (iterGrps.hasNext()) {
	    			iterGrps.next().close();
	    		}

            	this.whatIsOpen();
				NodeLogger.getLogger("HDF5 Files").info("File " + this.getName() + " closed: "
						+ H5.H5Fclose(this.getElementId()));
                this.setOpen(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
}
