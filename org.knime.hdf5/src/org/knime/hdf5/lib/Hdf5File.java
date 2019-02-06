package org.knime.hdf5.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.knime.core.node.NodeLogger;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

public class Hdf5File extends Hdf5Group {

	public static final int NOT_ACCESSED = -1;
	
	public static final int READ_ONLY_ACCESS = 0;
	
	public static final int READ_WRITE_ACCESS = 1;
	
	private static final long START = System.nanoTime();
	
	private static final List<Hdf5File> ALL_FILES = new ArrayList<>();

	private static final ReentrantReadWriteLock GLOBAL_RWL = new ReentrantReadWriteLock(true);
	
	private static final Lock GLOBAL_R = GLOBAL_RWL.readLock();
	
	private static final Lock GLOBAL_W = GLOBAL_RWL.writeLock();
	
	private final String m_filePath;
	
	private final ReentrantReadWriteLock m_rwl = new ReentrantReadWriteLock(true);
	
	private final Lock m_r = m_rwl.readLock();
	
	private final Lock m_w = m_rwl.writeLock();
	
	private int m_access = NOT_ACCESSED;
	
	private Map<Thread, Integer> m_accessors = new HashMap<>();
	
	/* TODO check if there are problems when creating/deleting dataSets in HdfView at the same time as in the HdfWriter
	 * (e.g. there are x, x(1), x(2) and x(3); delete and create x(2) again)
	 */
	private Hdf5File(final String filePath) throws HDF5LibraryException, NullPointerException,
			IllegalArgumentException {
		super(filePath.substring(filePath.lastIndexOf(File.separator) + 1));
		m_filePath = filePath;
		
		ALL_FILES.add(this);
	}
	
	/**
	 * Creating an instance creates a new file or, if there's already a file in this path, opens it. <br>
	 * If the File.separator is not '/', the part of the path after the last File.separator
	 * may not contain '/'.
	 * 
	 * @param filePath The whole path to the file including its name.
	 */
	public static Hdf5File createFile(final String filePath) throws IOException {
		try {
			GLOBAL_W.lock();
		
			if (new File(filePath).exists()) {
				throw new IOException("File does already exist");
			}
			
			Hdf5File file = new Hdf5File(filePath);
			file.create();
			
			return file;
			
		} catch (HDF5LibraryException | IOException | NullPointerException | IllegalArgumentException hlionpiae) {
			throw new IOException("The file \"" + filePath + "\" could not be created: " + hlionpiae.getMessage(), hlionpiae);
			
		} finally {
			GLOBAL_W.unlock();
		}
	}
	
	public synchronized static Hdf5File openFile(final String filePath, final int access) throws IOException {
		try {
			GLOBAL_R.lock();
		
			if (!new File(filePath).exists()) {
				throw new IOException("File does not exist");
			}
			
			Hdf5File file = null;
			for (Hdf5File f : ALL_FILES) {
				if (f.getFilePath().equals(filePath)) {
					file = f;
					file.open(access);
					break;
				}
			}
	
			if (file == null) {
				file = new Hdf5File(filePath);
				file.open(access);
			}
			
			return file;
			
		} catch (HDF5LibraryException | IOException | NullPointerException | IllegalArgumentException hlionpiae) {
			throw new IOException("The file \"" + filePath + "\" could not be opened: " + hlionpiae.getMessage(), hlionpiae);
			
		} finally {
			GLOBAL_R.unlock();
		}
	}

	public static boolean existsHdfFile(final String filePath) {
		try {
			GLOBAL_R.lock();
			return hasHdf5FileEnding(filePath) && new File(filePath).exists();
			
		} finally {
			GLOBAL_R.unlock();
		}
	}
	
	public static boolean isHdfFileCreatable(final String filePath, final boolean overwriteHdfFile) {
		// TODO maybe use org.knime.core.node.util.CheckUtils, but without possibility for url
		try {
			GLOBAL_R.lock();
			return hasHdf5FileEnding(filePath) && new File(getDirectoryPath(filePath)).isDirectory()
					&& (!new File(filePath).exists() || overwriteHdfFile);
			
		} finally {
			GLOBAL_R.unlock();
		}
	}
	
	public static boolean hasHdf5FileEnding(final String filePath) {
		return filePath.endsWith(".h5") || filePath.endsWith(".hdf5");
	}
	
	public static String getDirectoryPath(String filePath) {
		int dirPathLength = filePath.lastIndexOf(File.separator);
		return filePath.substring(0, dirPathLength >= 0 ? dirPathLength : 0);
	}

	public static String getUniqueFilePath(String filePath) throws IOException {
		File directory = new File(Hdf5File.getDirectoryPath(filePath));
		if (directory.isDirectory()) {
			String fileName = filePath.substring(filePath.lastIndexOf(File.separator) + 1);
			String fileExtension = fileName.lastIndexOf(".") >= 0 ? fileName.substring(fileName.lastIndexOf(".")) : "";
			String fileNameWithoutExtension = fileName.substring(0, fileName.length() - fileExtension.length());
			
			List<String> usedNames = new ArrayList<>();
			for (File file : directory.listFiles()) {
				if (file.isFile()) {
					String name = file.getName();
	    			String extension = name.lastIndexOf(".") >= 0 ? name.substring(name.lastIndexOf(".")) : "";
	    			if (extension.equals(fileExtension)) {
	    				usedNames.add(name.substring(0, name.length() - extension.length()));
	    			}
				}
			}
    		return directory.getPath() + File.separator + Hdf5TreeElement.getUniqueName(usedNames, fileNameWithoutExtension) + fileExtension;
    		
		} else {
			throw new IOException("Directory \"" + directory.getPath() + "\" for new file does not exist");
		}
	}
	
	public String getFilePath() {
		return m_filePath;
	}
	
	@Override
	protected boolean isOpen() {
		return isOpenInThisThread();
	}
	
	@Override
	public boolean exists() {
		return Hdf5File.existsHdfFile(m_filePath);
	}
	
	private boolean isOpenInThisThread() {
		synchronized (m_accessors) {
			return m_accessors.containsKey(Thread.currentThread());
		}
	}

	private void setOpenInThisThread(boolean open) {
		synchronized (m_accessors) {
			Thread curThread = Thread.currentThread();
			if (!isOpenInThisThread() && open) {
				m_accessors.put(curThread, 1);
			} else if (isOpenInThisThread()) {
				m_accessors.put(curThread, m_accessors.get(curThread) + (open ? 1 : -1));
			}
			
			String access = m_access == READ_ONLY_ACCESS ? "READ" : m_access == READ_WRITE_ACCESS ? "WRITE" : "NONE";
			System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + curThread + "\t\t" + access + "\t\"" + getName() + "\" is open " + m_accessors.get(curThread) + " times");
			
			if (m_accessors.get(curThread) == 0) {
				m_accessors.remove(curThread);
			}
		}
	}
	
	private boolean isOpenExactlyOnceInThisThread() {
		synchronized (m_accessors) {
			return isOpenInThisThread() && m_accessors.get(Thread.currentThread()) == 1;
		}
	}
	
	private boolean isOpenOnlyInThisThread() {
		return isOpenInThisThread() && m_accessors.size() == 1;
	}
	
	private boolean isOpenInAnyThread() {
		return !m_accessors.isEmpty();
	}
	
	public boolean isOpenAnywhere() throws IOException {
		try {
			GLOBAL_W.lock();
			checkExists();
			
			long fileId = H5.H5Fopen(getFilePath(), HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
			
			boolean openSomewhereElse = H5.H5Fget_obj_count(getElementId(), HDF5Constants.H5F_OBJ_FILE) > 1;
			
			H5.H5Fclose(fileId);
			
			return openSomewhereElse;
			
		} catch (HDF5LibraryException hle) {
			throw new IOException(hle.getMessage(), hle);
			
		} finally {
			GLOBAL_W.unlock();
		}
	}
	
	private void create() throws IOException {
		try {
			System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tLOCK \tWRITE \t\"" + getName() + "\" ...");
			m_w.lock();
			System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tLOCK \tWRITE \t\"" + getName() + "\" ... successful");
			
			try {
				lockWriteOpen();
				setElementId(H5.H5Fcreate(getFilePath(), HDF5Constants.H5F_ACC_EXCL,
						HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT));
				m_access = READ_WRITE_ACCESS;
				setOpenInThisThread(true);
				
			} finally {
				unlockWriteOpen();
			}
        } catch (HDF5LibraryException | NullPointerException hlnpe) {
			System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tWRITE \t\"" + getName() + "\" ...");
            m_w.unlock();
			System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tWRITE \t\"" + getName() + "\" ... successful");

			throw new IOException("The file \"" + getFilePath() + "\" cannot be created : " + hlnpe.getMessage(), hlnpe);
        }
	}
	
	public void open(final int access) throws IOException {
		try {
			if (!isOpenInThisThread()) {
    			long pid = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
    			H5.H5Pset_fclose_degree(pid, HDF5Constants.H5F_CLOSE_STRONG);
    			
				if (access == READ_ONLY_ACCESS) {
					System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tLOCK \tREAD \t\"" + getName() + "\" ...");
					m_r.lock();
					System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tLOCK \tREAD \t\"" + getName() + "\" ... successful");
					
					try {
						lockWriteOpen();
						if (!isOpenInAnyThread()) {
							setElementId(H5.H5Fopen(getFilePath(), HDF5Constants.H5F_ACC_RDONLY, pid));
						}
						m_access = access;
						setOpenInThisThread(true);
						
					} finally {
						unlockWriteOpen();
					}
				} else if (access == READ_WRITE_ACCESS) {
					System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tLOCK \tWRITE \t\"" + getName() + "\" ...");
					m_w.lock();
					System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tLOCK \tWRITE \t\"" + getName() + "\" ... successful");

					try {
						lockWriteOpen();
						if (!isOpenInAnyThread()) {
							setElementId(H5.H5Fopen(getFilePath(), HDF5Constants.H5F_ACC_RDWR, pid));
						}
						m_access = access;
						setOpenInThisThread(true);
						
					} finally {
						unlockWriteOpen();
					}
				}
				
    			H5.H5Pclose(pid);
    			
			} else {
				setOpenInThisThread(true);
			}
		} catch (HDF5LibraryException | NullPointerException hlnpe) {
			if (access == READ_ONLY_ACCESS) {
				System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tREAD \t\"" + getName() + "\" ...");
				m_r.unlock();
				System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tREAD \t\"" + getName() + "\" ... successful");
				
			} else if (access == READ_WRITE_ACCESS) {
				System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tWRITE \t\"" + getName() + "\" ...");
				m_w.unlock();
				System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tWRITE \t\"" + getName() + "\" ... successful");
			}

			throw new IOException("The file \"" + getFilePath() + "\" cannot be opened: " + hlnpe.getMessage(), hlnpe);
        }
	}
	
	public Hdf5File createBackup(String prefix) throws IOException {
		return copyFile(getUniqueFilePath(getDirectoryPath(m_filePath) + File.separator + prefix + getName()));
	}
	
	public Hdf5File copyFile(String newPath) throws IOException {
		GLOBAL_W.lock();
		
		Path backupPath = Files.copy(Paths.get(m_filePath), Paths.get(newPath), StandardCopyOption.COPY_ATTRIBUTES);
		Hdf5File file = openFile(backupPath.toString(), READ_ONLY_ACCESS);
		
		GLOBAL_W.unlock();
		
		return file;
	}

	public boolean deleteFile() throws IOException {
		// TODO maybe only allow deletion in write access; same for deletion of children
		// TODO check if some other threads are still waiting for this file
		
		GLOBAL_W.lock();
		
		File file = new File(m_filePath);
		if (file == null || !file.exists()) {
			throw new IOException("File cannot be deleted: it does not exist");
		}
		
		close();
		if (isOpenAnywhere()) {
			throw new IOException("File cannot be deleted: it is still opened somewhere");
		}
		
		boolean success = file.delete();
		if (success) {
			ALL_FILES.remove(this);
		}
		
		GLOBAL_W.unlock();
		
		return success;
	}
	
	private String whatisOpenInFile() {
        long count = -1;
        long openObjects = -1;
        long[] objects;
        String[] objTypes = { "Unknown objectType", "File", "Group", "DataType", "DataSpace", "DataSet", "Attribute" };
        String opened = "";
		
        try {
        	if (isOpenInThisThread()) {
        		count = H5.H5Fget_obj_count(getElementId(), HDF5Constants.H5F_OBJ_ALL);
        		
			} else {
				return "(error: file is already closed)";
			}
		} catch (HDF5LibraryException hle) {
			NodeLogger.getLogger("HDF5 Files").debug("Number of open objects in file could not be loaded: " + hle.getMessage(), hle);
		}

        if (count <= 0) {
        	return "(error: couldn't find out number of open objects)";
        }
        
        if (count == 1) {
        	return "0";
        }

        objects = new long[(int) count];
		opened += count - 1;

        try {
			openObjects = H5.H5Fget_obj_ids(getElementId(), HDF5Constants.H5F_OBJ_ALL, count, objects);
			   
			// i = 0 is just the file itself
			int i = 1;
	        if (i < openObjects) {
	    		String pathFromFile = H5.H5Iget_name(objects[i]);
	    		opened += " [\"" + pathFromFile + "\"";
	    		if (H5.H5Iis_valid(objects[i])) {
		    		String objectType = objTypes[(int) H5.H5Iget_type(objects[i])];
		    		opened += " (" + objectType + ")";
	    		}
	        }
	        for (i = 2; i < openObjects; i++) {
        		String pathFromFile = H5.H5Iget_name(objects[i]);
	    		opened += ", \"" + pathFromFile + "\"";
	    		if (H5.H5Iis_valid(objects[i])) {
		        	String objectType = objTypes[(int) H5.H5Iget_type(objects[i])];
		    		opened += " (" + objectType + ")";
	    		}
	        }
	        
	        opened += "]";
	        
	    // TODO change to 'Exception e'
		} catch (HDF5LibraryException | NullPointerException hlnpe) {
            NodeLogger.getLogger("HDF5 Files").debug("Info of open objects in file could not be loaded: " + hlnpe.getMessage(), hlnpe);
        }
        
        return opened;
	}
/*
	private synchronized static String whatisOpenInHdf() {
		String opened = "";
		
		try {
			int fileNum = 0;
			for (long id : H5.getOpenIDs()) {
				if (H5.H5Iis_valid(id) && H5.H5Iget_type(id) == HDF5Constants.H5I_FILE) {
					fileNum++;
				}
			}
			
			opened = H5.getOpenIDCount() + " objects, thereof " + (fileNum - 1) + " other files " + H5.getOpenIDs();
			
	    // TODO change to 'Exception e'
		} catch (HDF5LibraryException hle) {
            NodeLogger.getLogger("HDF5 Files").debug("Info of open objects in total could not be loaded: " + hle.getMessage(), hle);
		}
		
		return opened;
	}
	*/
	/**
	 * Closes the group and all elements in this group.
	 * @throws IOException 
	 * 
	 */
	@Override
	public boolean close() throws IOException {
		try {
			lockWriteOpen();
			checkExists();
			
			boolean success = true;
			if (isOpenInThisThread()) {
				if (isOpenExactlyOnceInThisThread()) {
	    			if (isOpenOnlyInThisThread()) {
	    	    		for (Hdf5DataSet<?> ds : getDataSets()) {
	            			success &= ds.close();
	            		}

	    	    		for (Hdf5Attribute<?> attr : getAttributes()) {
	            			success &= attr.close();
	            		}
	    	    		
	    	    		for (Hdf5Group group : getGroups()) {
	            			success &= group.close();
	            		}
			    		
			    		String whatIsOpenInFile = whatisOpenInFile();
			    		NodeLogger.getLogger("HDF5 Files").debug("Number of open objects in file \""
			    				+ getName() + "\": " + whatIsOpenInFile/* + " (total number: " + whatisOpenInHdf() + ")"*/);

			    		// System.out.println(whatisOpenInHdf());
			    		System.out.println(whatIsOpenInFile);
			    		
			    		success &= H5.H5Fclose(getElementId()) >= 0;
			    		
			    		/*if (!whatIsOpenInFile.equals("0")) {
			    			long pid = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
			    			H5.H5Pset_fclose_degree(pid, HDF5Constants.H5F_CLOSE_SEMI);
			    			long fileId = H5.H5Fopen(getFilePath(), HDF5Constants.H5F_ACC_RDONLY, pid);
			    			
			    			System.out.println("Close file: " + H5.H5Fclose(fileId));
			    		}*/
		    		}
	    			
	    			if (success) {
			    		setOpenInThisThread(false);
	  
						if (m_access == READ_ONLY_ACCESS) {
							System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tREAD \t\"" + getName() + "\" ...");
							m_r.unlock();
							System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tREAD \t\"" + getName() + "\" ... successful");
						} else if (m_access == READ_WRITE_ACCESS) {
							System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tWRITE \t\"" + getName() + "\" ...");
							m_w.unlock();
							System.out.println(String.format("%,20d", System.nanoTime() - START) + " " + Thread.currentThread() + " \tUNLOCK \tWRITE \t\"" + getName() + "\" ... successful");
						}
						
			    		if (!isOpenInAnyThread()) {
			    			m_access = NOT_ACCESSED;
						}
	    			}
	            } else {
	    			setOpenInThisThread(false);
	            }
			}
            
            return success;
            
        } catch (HDF5LibraryException hle) {
        	throw new IOException("File \"" + getFilePath() + "\" could not be closed: " + hle.getMessage(), hle);
        	
        } finally {
			unlockWriteOpen();
		}
	}
}
