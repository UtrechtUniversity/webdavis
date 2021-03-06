package webdavis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.SimpleTimeZone;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class contains static utility methods for the Davis servlet
 * and its associated classes.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DavisUtilities {

    /**
     * Depth constant indicating that the operation applies only to the
     * targeted resource itself.
     */
    public static final int RESOURCE_ONLY_DEPTH = 0;

    /**
     * Depth constant indicating that the operation applies to the targeted
     * resource and its immediate children.
     */
    public static final int CHILDREN_DEPTH = 1;

    /**
     * Depth constant indicating that the operation applies to the targeted
     * resource and all of its progeny.
     */
    public static final int INFINITE_DEPTH = -1;

    /**
     * Timeout constant indicating an unspecified lock timeout.
     */
    public static final long UNSPECIFIED_TIMEOUT = 0l;

    /**
     * Timeout constant indicating an infinite lock timeout.
     */
    public static final long INFINITE_TIMEOUT = -1l;

    /**
     * Timeout constant representing the maximum lock timeout value.
     */
    public static final long MAXIMUM_TIMEOUT = 0xffffffffl;

    private static final DateFormat CREATION_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    private static final DateFormat LAST_MODIFIED_FORMAT =
            new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.US);

    private static final Random RANDOM = new Random();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static MessageDigest digest;

	private static ServletConfig servletConfig;
    
    static {
        TimeZone gmt = new SimpleTimeZone(0, "GMT");
        CREATION_FORMAT.setTimeZone(gmt);
        LAST_MODIFIED_FORMAT.setTimeZone(gmt);
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(getResource(DavisUtilities.class,
                    "md5Unavailable", null, null));
        }
    }
    
	
    private DavisUtilities() { }
    
    public static void init(ServletConfig config) {
    	servletConfig = config;
    }
    
    /**
     * Returns the specified resource string value.
     *
     * @param context A class representing the context for the resource
     * string.
     * @param resource The resource name.
     * @param parameters Substitution parameters for the message.
     * @param locale The desired locale.
     * @return A <code>String</code> containing the resource value.
     */
    public static String getResource(Class context, String resource,
            Object[] parameters, Locale locale) {
        ResourceBundle resources = (locale == null) ?
                ResourceBundle.getBundle("webdavis.Resources") :
                        ResourceBundle.getBundle("webdavis.Resources", locale);
        String pattern = (context != null) ? resources.getString(
                context.getName() + "." + resource) :
                        resources.getString(resource);
        return (parameters == null) ? pattern :
                MessageFormat.format(pattern, parameters);
    }

    /**
     * Formats a timestamp (representing milliseconds since the epoch)
     * as used in the WebDAV <code>creationdate</code> property.
     *
     * @param creation The creation timestamp, represented as the number
     * of milliseconds since midnight, January 1, 1970 UTC.
     * @return A <code>String</code> containing the formatted result.
     */
    public static String formatCreationDate(long creation) {
        synchronized (CREATION_FORMAT) {
            return CREATION_FORMAT.format(new Date(creation));
        }
    }

    /**
     * Formats a timestamp (representing milliseconds since the epoch)
     * as used in the WebDAV <code>getlastmodified</code> property.
     *
     * @param lastModified The last modification timestamp, represented
     * as the number of milliseconds since midnight, January 1, 1970 UTC.
     * @return A <code>String</code> containing the formatted result.
     */
    public static String formatGetLastModified(long lastModified) {
        synchronized (LAST_MODIFIED_FORMAT) {
            return LAST_MODIFIED_FORMAT.format(new Date(lastModified));
        }
    }

    /**
     * Returns the entity tag for the specified resource.  The returned
     * string uniquely identifies the current incarnation of the given
     * resource.
     *
     * @param file The resource whose entity tag is to be retrieved.
     * @return A <code>String</code> containing the entity tag for the
     * resource.
     */
    public static String getETag(IRODSFile file) {
        if (file == null) return null;
        try {
            if (!file.isFile()) return null;
            String key = file.toString() + ":" +
                    Long.toHexString(file.lastModified());
            byte[] hashBytes = null;
            synchronized (digest) {
                hashBytes = digest.digest(key.getBytes("UTF-8"));
            }
            StringBuffer hash = new StringBuffer();
            int count = hashBytes.length;
            for (int i = 0; i < count; i++) {
                hash.append(Integer.toHexString((hashBytes[i] >> 4) & 0x0f));
                hash.append(Integer.toHexString(hashBytes[i] & 0x0f));
            }
            return "\"" + hash.toString() + "\"";
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Utility method to parse the "Depth" header.
     *
     * @param depth The value of the WebDAV "Depth" header.
     * @return An <code>int</code> containing the depth code.  One of
     * <code>RESOURCE_ONLY_DEPTH</code>, <code>CHILDREN_DEPTH</code>, or
     * <code>INFINITE_DEPTH</code>.
     */ 
    public static int parseDepth(String depth) {
        if (depth == null) return INFINITE_DEPTH;
        if ("0".equals(depth)) return RESOURCE_ONLY_DEPTH;
        return "1".equals(depth) ? CHILDREN_DEPTH : INFINITE_DEPTH;
    }

    /**
     * Utility method to format a lock timeout value for use in the
     * "Timeout" header.
     *
     * @param timeout The timeout value in milliseconds.
     * @return A <code>String</code> containing the formatted timeout
     * value.  If the provided timeout is <code>UNSPECIFIED_TIMEOUT</code>,
     * this method returns <code>null</code>.  If the value is
     * <code>INFINITE_TIMEOUT</code>, this returns the string
     * "<code>Infinite</code>".  For all other values, this method returns
     * a string of the form "<code>Second-<i>nnnn</i></code>", where
     * <code><i>nnnn</i></code> is the timeout value converted to seconds.
     * If the value is greater than <code>MAXIMUM_TIMEOUT</code>,
     * <code>MAXIMUM_TIMEOUT</code> will be used instead.
     */
    public static String formatTimeout(long timeout) {
        if (timeout == UNSPECIFIED_TIMEOUT) return null;
        if (timeout == INFINITE_TIMEOUT) return "Infinite";
        timeout = timeout / 1000l;
        if (timeout > MAXIMUM_TIMEOUT) timeout = MAXIMUM_TIMEOUT;
        return "Second-" + timeout;
    }

    /**
     * Utility method to parse the "Timeout" header.  This implementation
     * recognizes the "Infinite" keyword, as well as the "Second-xxxxx"
     * format.
     *
     * @param timeout The value of the WebDAV "Timeout" header.
     * @return A <code>long</code> containing the timeout value in
     * milliseconds.  <code>UNSPECIFIED_TIMEOUT</code> indicates no timeout
     * value was specified or recognized.  <code>INFINITE_TIMEOUT</code>
     * indicates an infinite timeout was requested.
     */
    public static long parseTimeout(String timeout) {
        if (timeout == null) return UNSPECIFIED_TIMEOUT;
        StringTokenizer tokenizer = new StringTokenizer(timeout, ", ");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim().toLowerCase();
            if ("infinite".equalsIgnoreCase(token)) return INFINITE_TIMEOUT;
            if (!token.startsWith("second-")) continue;
            try {
                long value =
                        Long.parseLong(token.substring("second-".length())) *
                                1000l;
                return (value > MAXIMUM_TIMEOUT) ? MAXIMUM_TIMEOUT : value;
            } catch (Exception ex) { }
        }
        return UNSPECIFIED_TIMEOUT;
    }

    /**
     * Utility method to output the "lockdiscovery" XML for the active locks
     * on the specified SMB resource, as managed by the specified lock manager.
     *
     * @param file The SMB resource whose active locks are to be enumerated.
     * @param lockManager The lock manager to examine.
     * @param destination The element under which the active locks will be
     * enumerated.  This would typically be a "lockdiscovery" element.
     */ 
    public static void lockDiscovery(IRODSFile file, LockManager lockManager,
            Element destination) throws IOException {
        if (file == null || lockManager == null || destination == null) return;
        Lock[] activeLocks = lockManager.getActiveLocks(file);
        if (activeLocks == null || activeLocks.length == 0) return;
        for (int i = activeLocks.length - 1; i >= 0; i--) {
            Lock lock = activeLocks[i];
            Element activeLock = createElement(destination, "activelock"); 
            Element lockType = createElement(activeLock, "locktype");
            lockType.appendChild(createElement(lockType, "write"));
            activeLock.appendChild(lockType);
            Element lockScope = createElement(activeLock, "lockscope");
            lockScope.appendChild(createElement(lockScope,
                    lock.isExclusive() ? "exclusive" : "shared"));
            activeLock.appendChild(lockScope);
            Element depth = createElement(activeLock, "depth");
            depth.appendChild(depth.getOwnerDocument().createTextNode(
                    (lock.getDepth() == RESOURCE_ONLY_DEPTH) ? "0" :
                            "infinity"));
            activeLock.appendChild(depth);
            DocumentFragment ownerValue = lock.getOwner();
            if (ownerValue != null) {
                Element owner = createElement(activeLock, "owner");
                owner.appendChild(owner.getOwnerDocument().importNode(
                        ownerValue, true));
                activeLock.appendChild(owner);
            }
            long timeoutValue = lock.getTimeout();
            if (timeoutValue != UNSPECIFIED_TIMEOUT) {
                Element timeout = createElement(activeLock, "timeout");
                timeout.appendChild(timeout.getOwnerDocument().createTextNode(
                        formatTimeout(timeoutValue)));
                activeLock.appendChild(timeout);
            }
            String token = lock.getToken();
            if (token != null) {
                Element locktoken = createElement(activeLock, "locktoken");
                Element href = createElement(locktoken, "href");
                href.appendChild(href.getOwnerDocument().createTextNode(token));
                locktoken.appendChild(href);
                activeLock.appendChild(locktoken);
            }
            destination.appendChild(activeLock);
        }
    }

    /**
     * Generates a UUID, as described in ISO-11578.
     *
     * @return A <code>String</code> containing a globally unique UUID.
     */
    public static String generateUuid() {
        byte[] data = new byte[16];
        synchronized (RANDOM) {
            RANDOM.nextBytes(data);
        }
        data[8] = (byte) (data[8] & 0xbf | 0x80);
        data[6] = (byte) (data[6] & 0x4f | 0x40);
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 16; i++) {
            buffer.append(HEX[(data[i] >> 4) & 0x0f]);
            buffer.append(HEX[data[i] & 0x0f]);
        }
        buffer.insert(8, '-');
        buffer.insert(13, '-');
        buffer.insert(18, '-');
        buffer.insert(23, '-');
        return buffer.toString();
    }

    private static Element createElement(Element base, String tag) {
        String namespace = base.getNamespaceURI();
        if (namespace != null) {
            String prefix = base.getPrefix();
            return base.getOwnerDocument().createElementNS(namespace,
                    prefix == null ? tag : prefix + ":" + tag);
        }
        return base.getOwnerDocument().createElement(tag);
    }

    public static String preprocess(String document, Hashtable<String, String> substitutions) {
    	
		// Make substitutions in file
		for (Enumeration<String> keys = substitutions.keys(); keys.hasMoreElements();) {
			String key = keys.nextElement();
			String replacement = substitutions.get(key);
			if (replacement == null)
				replacement = "";
			document = document.replace("<parameter " + key + "/>", replacement);
		}
		return document;
    }
    
    public static String loadResource(String fileName) {
    	
		String result = "";
		try {
			InputStream stream = getResourceAsStream(fileName);
			if (stream == null)
				throw new IOException("can't open file");
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			char[] buffer = new char[1024];
			int numRead = 0;
			while ((numRead = reader.read(buffer)) != -1) {
				String readData = String.valueOf(buffer, 0, numRead);
				result += (readData);
			}
			reader.close();
		} catch (IOException e) {
			Log.log(Log.CRITICAL, "Failed to read file "+fileName+": " + e);
		}
		return result;
    }
    
	public static InputStream getResourceAsStream(String location, Locale locale) {
		
		int index = location.indexOf('.');
		String prefix = (index != -1) ? location.substring(0, index) : location;
		String suffix = (index != -1) ? location.substring(index) : "";
		String language = locale.getLanguage();
		String country = locale.getCountry();
		String variant = locale.getVariant();
		InputStream stream = null;
		if (!variant.equals("")) {
			stream = getResourceAsStream(prefix + '_' + language + '_' + country + '_' + variant + suffix);
			if (stream != null)
				return stream;
		}
		if (!country.equals("")) {
			stream = getResourceAsStream(prefix + '_' + language + '_' + country + suffix);
			if (stream != null)
				return stream;
		}
		stream = getResourceAsStream(prefix + '_' + language + suffix);
		if (stream != null)
			return stream;
		Locale secondary = Locale.getDefault();
		if (!locale.equals(secondary)) {
			language = secondary.getLanguage();
			country = secondary.getCountry();
			variant = secondary.getVariant();
			if (!variant.equals("")) {
				stream = getResourceAsStream(prefix + '_' + language + '_' + country + '_' + variant + suffix);
				if (stream != null)
					return stream;
			}
			if (!country.equals("")) {
				stream = getResourceAsStream(prefix + '_' + language + '_' + country + suffix);
				if (stream != null)
					return stream;
			}
			stream = getResourceAsStream(prefix + '_' + language + suffix);
			if (stream != null)
				return stream;
		}
		return getResourceAsStream(location);
	}

	public static InputStream getResourceAsStream(String location) {
		
		InputStream stream = null;
		try {
			stream = servletConfig.getServletContext().getResourceAsStream(location);
			if (stream != null)
				return stream;
		} catch (Exception ex) {}
//		try {
//			stream = getClass().getResourceAsStream(location);
//			if (stream != null)
//				return stream;
//		} catch (Exception ex) {}
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader != null)
				stream = loader.getResourceAsStream(location);
			if (stream != null)
				return stream;
		} catch (Exception ex) {}
		try {
			ClassLoader loader = ClassLoader.getSystemClassLoader();
			if (loader != null)
				stream = loader.getResourceAsStream(location);
			if (stream != null)
				return stream;
		} catch (Exception ex) {}
		return null;
	}
	
	public static Cookie getCookie(HttpServletRequest request, String name) {

		Cookie[] cookies = request.getCookies();
		if (cookies == null)
			return null;
		for (Cookie cookie:cookies)
			if (cookie.getName().equals(name)) 
				return cookie;
		return null;
	}
    
    /**
     * Dumps a DOM tree to a PrintStream
     *
     */
    public static void dump(Node root, PrintStream out) {   	
    	dump(root, "", out);
    }
    private static void dump(Node root, String prefix, PrintStream out) {
    	
    	out.print(prefix);
    	if (root instanceof Element) {
    		out.print("<"+((Element)root).getTagName()+">");
    	} else if (root instanceof CharacterData) {
    		out.print(((CharacterData)root).getData());
    	} else 
    		out.print(root);

    	NamedNodeMap attrs = root.getAttributes();
    	if (attrs != null) {
    		int len = attrs.getLength();
    		for (int i = 0; i < len; i++) {
    			Node attr = attrs.item(i); 
    			out.print("  attr"+i+"= '"+attr.toString().trim()+"'");
    		}
    	}
    	out.println();

    	if (root.hasChildNodes()) {
    		NodeList children = root.getChildNodes();
    		if (children != null) {
    			int len = children.getLength();
    			for (int i = 0; i < len; i++)
    				dump(children.item(i), prefix+"    ", out); 
    		}
    	}
    }
    
    public static String arrayToString(String[] array, String delim) {
    	
    	String s = "";
    	if (array == null)
    		return null;
    	for (int i = 0; i < array.length; i++) {
    		if (i > 0)
    			s += delim;
    		s += array[i];
    	}	           
    	return s;
    }
    
    public static String encodeFileName(String fileName) {
    	
    	try {
    		java.net.URI uri = new java.net.URI(null, null, fileName, null);
  //  		System.err.println("uri="+uri);
  			return uri.toString();
    	} catch (Exception e) {
    		Log.log(Log.DEBUG, "Failed to encode file name: bad uri:"+fileName);
//    		String s = null;
//      	  	try {
//      	  		s = URLEncoder.encode(fileName, "UTF-8");
//      	  	} catch (UnsupportedEncodingException e2) {}
//      	  	s = s.replace("+", "%2B");
//      	  	return s;
    	}
    	return null;
    }
    
    public static String getStackTrace(Throwable e) {
    	
		StringWriter trace = new StringWriter();
		e.printStackTrace(new PrintWriter(trace));
		return trace.toString();
    }
    
    final static Hashtable <String, String> permMap = new Hashtable<String, String>();
    static {
	    permMap.put("OWN", "read and write");
	    permMap.put("WRITE", "write only");
	    permMap.put("READ", "read only");
	    permMap.put("NULL", "no access");
    }

    public static String iPermissionToPermission(String p) {
    	return permMap.get(p);
    }

  	public static String loadUIInclude(String uiIncludeString) {

  		if (isUIIncludeAFile(uiIncludeString)) {
  			uiIncludeString = DavisUtilities.loadResource(uiIncludeString);
  		}
 	  
  		return uiIncludeString;	
  	}
  	
  	// This method checks whether a uiInclude is a file name or content string
  	private static boolean isUIIncludeAFile(String uiInclude) {

  		boolean isFile = false;	
  		
  		if (uiInclude != null && !uiInclude.isEmpty()) {
  			if (!uiInclude.startsWith("<")) {
  				isFile = true;	
  			}
  		}
  	  
  		return isFile;
  	}
}
