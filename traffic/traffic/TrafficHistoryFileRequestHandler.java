package traffic;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * HTTP requests for traffic file data, with caching.
 * Responds with JSON for a JSONP request.
 * @author alan
 *
 */
public class TrafficHistoryFileRequestHandler implements HttpHandler {
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	private static final LoadingCache<String, String> FILE_CACHE = CacheBuilder.newBuilder()
			.maximumSize(20)
			.expireAfterWrite(1, TimeUnit.DAYS)
			.build(new CacheLoader<String, String>() {
				@Override
				public String load(String key) throws Exception {
					return TrafficHistoryFileRequestHandler.loadFromFilesystem(key);
				}
			});

	public static HttpHandler create() {
		return new TrafficHistoryFileRequestHandler();
	}

	@Override
	public void handle(HttpExchange t) throws IOException {
		Headers responseHeaders = t.getResponseHeaders();
		responseHeaders.set("Content-Type", "text/javascript");
		QueryContents qc = parseQuery(t.getRequestURI().getQuery());
		String fileData = getFileData(qc.getDataKey());
		OutputStream os = t.getResponseBody();
		if (null != fileData) {
			String callback = qc.getCallback();
			StringBuilder newResponse = new StringBuilder();
			newResponse.append(callback).append("(").append(fileData).append(");");
			byte[] resBytes = newResponse.toString().getBytes();
			t.sendResponseHeaders(200, resBytes.length);
			copy(new ByteArrayInputStream(resBytes), os);
		} else {
			String err = "Error loading file";
			t.sendResponseHeaders(503, err.length());
			os.write(err.getBytes());
		}
		os.close();
	}

	private QueryContents parseQuery(String query) {
		String[] fields = query.split("&");
		return QueryContents.create(fields[0], fields[1]);
	}

	private String getFileData(String key) throws FileNotFoundException {
//		System.out.println("got " + query);
		try {
			String data = FILE_CACHE.get(key);
			if (null == data) {
				data = loadFromFilesystem(key);
			}
			
//			return data.replaceAll("\"", "'");
			return data;
		} catch (Exception e) {
			System.err.println(e.getMessage());
			return null;
		}
	}

	private static String loadFromFilesystem(String query) throws IOException {
		String data;
		final Path path = FileSystems.getDefault().getPath(DataLogger.LOG_PATH, query + DataLogger.LOG_FILE_EXT);
//		data = Files.readAllBytes(path);
		Scanner s = new Scanner(path);
		String header = s.nextLine(); // header
		String[] fields = header.split(",");
		JSONArray dataArray = new JSONArray();
		JSONArray timeArray = new JSONArray();
		JSONArray speedArray = new JSONArray();
		timeArray.put(fields[0]);
		speedArray.put(fields[1]);
		while (s.hasNext()) {
			String[] value = s.nextLine().split(",");
			timeArray.put(value[0]);
			speedArray.put(value[1]);
		}
		dataArray.put(timeArray);
		dataArray.put(speedArray);
		s.close();
		data = dataArray.toString();
		FILE_CACHE.put(query, data);
		return data;
	}

	private int copy(InputStream input, OutputStream output) throws IOException {
	  long count = copyLarge(input, output);
	  if (count > Integer.MAX_VALUE) {
	    return -1;
	  }
	  return (int) count;
	}

	private long copyLarge(InputStream input, OutputStream output) throws IOException {
	   byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
	   long count = 0;
	   int n = 0;
	   while (-1 != (n = input.read(buffer))) {
	     output.write(buffer, 0, n);
	     count += n;
	   }
	   return count;
	}
	
	private static class QueryContents {
		private String dataKey;
		private String callback;
		public static QueryContents create(String dataKey, String callback) {
			return new QueryContents(dataKey, callback);
		}
		public QueryContents(String dataKey, String callback) {
			this.dataKey = dataKey;
			this.callback = callback.split("=")[1];
		}
		public String getDataKey() {
			return dataKey;
		}
		public String getCallback() {
			return callback;
		}
	}

}