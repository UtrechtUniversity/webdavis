package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;

/**
 * Default implementation of a handler for requests using the WebDAV
 * MOVE method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultMoveHandler extends AbstractHandler {

    /**
     * Services requests which use the WebDAV MOVE method.
     * This implementation moves the source file to the destination location.
     * <br>
     * If the source file does not exist, a 404 (Not Found) error is sent
     * to the client.
     * <br>
     * If the destination is not specified, a 400 (Bad Request) error is
     * sent to the client.
     * <br>
     * If the destination already exists, and the client has sent the
     * "Overwrite" request header with a value of "T", then the request
     * succeeds and the file is overwritten.  If the "Overwrite" header is
     * not provided, a 412 (Precondition Failed) error is sent to the client.
     * <br>
     * If the destination was created, but the source could not be removed,
     * or if the move fails in batch mode,
     * a 403 (Forbidden) error is sent to the client.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws SerlvetException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request, HttpServletResponse response, DavisSession davisSession) throws ServletException, IOException {
 
    	response.setContentType("text/html; charset=\"utf-8\"");
        ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();
    	boolean batch = getFileList(request, davisSession, fileList, getJSONContent(request));
    	String destinationField = request.getHeader("Destination");
    	if (destinationField.indexOf("://") < 0)	// If destination field is a relative path, prepend a protocol for getRemoteURL()
    		destinationField = "http://"+destinationField;
    	String destination = getRemoteURL(request, destinationField);
		if (destination == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
		IRODSFile destinationFile = getIRODSFile(destination, davisSession);
        Iterator<IRODSFile> iterator = fileList.iterator();
        int result = 0;
        while (iterator.hasNext()) {
        	IRODSFile sourceFile = iterator.next();
			Log.log(Log.DEBUG, "moving: "+sourceFile+" to "+destinationFile);
            if (destinationFile.getAbsolutePath().equals(sourceFile.getAbsolutePath())) {
            	if (batch)
            		response.sendError(HttpServletResponse.SC_NO_CONTENT);
            	return;
        	}
			result = moveFile(request, davisSession, sourceFile, destinationFile, batch);
			if (result != HttpServletResponse.SC_NO_CONTENT && result != HttpServletResponse.SC_CREATED) {
				if (batch) {
	    			String s = "Failed to move '"+sourceFile.getAbsolutePath()+"'";
	    			Log.log(Log.WARNING, s);
	    			response.sendError(result, s); // Batch move failed
	    		} else
		    		response.sendError(result);
				return;
			}
        }
		response.setStatus(result);
		response.flushBuffer();
    }
    
    private int moveFile(HttpServletRequest request, DavisSession davisSession, IRODSFile file, IRODSFile destinationFile, boolean batch) throws IOException {
    	
        if (!file.exists()) 
            return HttpServletResponse.SC_NOT_FOUND;
        
        if (batch) {
        	destinationFile.mkdirs(); // Make sure destination directory exists
            destinationFile = getIRODSFile(destinationFile.getAbsolutePath()+IRODSFile.PATH_SEPARATOR+file.getName(), davisSession);
        } else {
            int result = checkLockOwnership(request, file);
        	if (result != HttpServletResponse.SC_OK) 
        		return result;
            result = checkLockOwnership(request, destinationFile);
        	if (result != HttpServletResponse.SC_OK) 
        		return result;
        	result = checkConditionalRequest(request, davisSession, file);
        	if (result != HttpServletResponse.SC_OK) 
        		return result;
        	result = checkConditionalRequest(request, davisSession, destinationFile);
        	if (result != HttpServletResponse.SC_OK) 
        		return result;
        	LockManager lockManager = getLockManager();
        	if (lockManager != null) {
        		file = lockManager.getLockedResource(file, davisSession);
        		destinationFile = lockManager.getLockedResource(destinationFile, davisSession);
        	}
        }
        Log.log(Log.DEBUG, "file:"+file.getAbsolutePath()+" destinationFile:"+destinationFile.getAbsolutePath());

        boolean overwritten = false;
        if (destinationFile.exists()) {
        	if ("T".equalsIgnoreCase(request.getHeader("Overwrite"))) {
        		destinationFile.delete();
                overwritten = true;
            } else 
                return HttpServletResponse.SC_PRECONDITION_FAILED;
        }
    	if (davisSession.getDefaultResource() != null && davisSession.getDefaultResource().length() > 0) {
//else 
	//        if (file.getFileSystem() instanceof IRODSFileSystem) {
	//        	        	((IRODSFile)file).setResource(davisSession.getDefaultResource());
	//        	        	((IRODSFile)destinationFile).setResource(davisSession.getDefaultResource());
	//        	        	((IRODSFile)destinationFile).setResource(((IRODSFile)file).getResource());
	//        }
    	}
        if (!file.renameTo(destinationFile)) {
        	// Jargon sometimes returns false when the rename seems to have worked, so check
        	if (!destinationFile.exists() || file.exists()) 
        		return HttpServletResponse.SC_FORBIDDEN;
        	Log.log(Log.DEBUG, "Move appears to have succeeded, so ignoring failure");
        }
      
        return overwritten ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED;
    }
}
