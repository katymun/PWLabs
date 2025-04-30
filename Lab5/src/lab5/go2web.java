package lab5;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class go2web {
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String USER_AGENT = "go2web/1.0";
    private static Map<String, CachedResponse> cache = new HashMap<>();

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        try {
            switch (args[0]) {
                case "-u":
                    if (args.length < 2) {
                        System.out.println("Error: URL is required with -u option");
                        printHelp();
                        return;
                    }
                    fetchUrl(args[1]);
                    break;
                case "-s":
                    if (args.length < 2) {
                        System.out.println("Error: Search term is required with -s option");
                        printHelp();
                        return;
                    }

                    StringBuilder searchTerm = new StringBuilder(args[1]);
                    for (int i = 2; i < args.length; i++) {
                        searchTerm.append(" ").append(args[i]);
                    }
                    searchTerm(searchTerm.toString());
                    break;
                case "-h":
                    printHelp();
                    break;
                default:
                    System.out.println("Unknown option: " + args[0]);
                    printHelp();
                    break;
            }
        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void searchTerm(String searchTerm) {
        String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = "https://www.google.com/search?q=" + encodedSearchTerm;

        URL parsedUrl = null;
        try {
            parsedUrl = parseUrl(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        String host = parsedUrl.getHost();
        String path = parsedUrl.getPath() + "?q=" +  encodedSearchTerm;

        String response = fetchHttpsUrl(host, path);

        HttpResponse httpResponse = parseHttpResponse(response);

        List<SearchResult> searchResults = extractSearchResults(httpResponse.body);

        int count = 0;
        for (SearchResult result : searchResults) {
            if (count >= 10) break;
            System.out.println((count + 1) + ". " + result.title);
            System.out.println("   URL: " + result.url);
            System.out.println();
            count++;
        }
    }

    private static void printHelp() {
        System.out.println("go2web - simple web client");
        System.out.println("Usage:");
        System.out.println("\tgo2web -u <URL>          #make an HTTP request to the specified URL and print the response");
        System.out.println("\tgo2web -s <search-term>  #make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("\tgo2web -h                #show this help");
    }

    private static void fetchUrl(String url) throws Exception {
        if (cache.containsKey(url) && !cache.get(url).isExpired()) {
            System.out.println("(Cached) " + url);
            System.out.println(cache.get(url).getContent());
            return;
        }
        URL parsedUrl = parseUrl(url);
        String host = parsedUrl.getHost();
        String path = parsedUrl.getPath().isEmpty() ? "/" :  parsedUrl.getPath();
        String query = parsedUrl.getQuery();
        if (query != null) {
            path += "?" + query;
        }
        
        String response;
        if (parsedUrl.getProtocol().equals("https")) {
            response = fetchHttpsUrl(host, path);
        } else {
            response = fetchHttpUrl(host, path);
        }
        
        HttpResponse httpResponse = parseHttpResponse(response);
    }

    private static HttpResponse parseHttpResponse(String response) {
        HttpResponse httpResponse = new HttpResponse();

        int headerBodySeparator = response.indexOf("\r\n\r\n");
        if (headerBodySeparator == -1) {
            httpResponse.body = "";
            String headersStr = response;
        } else {
            String headersStr = response.substring(0, headerBodySeparator);
            httpResponse.body = response.substring(headerBodySeparator + 4);

            if (httpResponse.body.contains("\r\n")) {
                String[] lines = httpResponse.body.split("\r\n");
                if (lines.length > 1 && lines[0].matches("[0-9a-fA-F]+")) {
                    StringBuilder decodedBody = new StringBuilder();
                    int i = 0;
                    while (i < lines.length) {
                        if (lines[i].matches("[0-9a-fA-F]+")) {
                            int chunkSize = Integer.parseInt(lines[i], 16);
                            if (chunkSize == 0) break;
                            i++;
                            if (i < lines.length) {
                                decodedBody.append(lines[i]);
                                i++;
                            }
                        } else {
                            i++;
                        }
                    }
                    httpResponse.body = decodedBody.toString();
                }
            }

            String[] headers = headersStr.split("\r\n");
            if (headers.length > 0) {
                String statusLine = headers[0];
                String[] statusParts = statusLine.split(" ", 3);
                if (statusParts.length >= 2) {
                    httpResponse.statusCode = Integer.parseInt(statusParts[1]);
                }

                for (int i = 1; i < headers.length; i++) {
                    int colonPos = headers[i].indexOf(":");
                    if (colonPos > 0) {
                        String nume = headers[i].substring(0, colonPos).trim();
                        String value = headers[i].substring(colonPos + 1).trim();
                        httpResponse.headers.put(nume, value);
                    }
                }
            }
        }
        return httpResponse;
    }

    private static String fetchHttpUrl(String host, String path) {
        try (Socket socket = new Socket(host, DEFAULT_HTTP_PORT)) {
            String request = "GET " + path + " HTTP/1.1\r\n" +
                             "Host: " + host + "\r\n" +
                             "User-Agent: " + USER_AGENT + "\r\n" +
                             "Accept: text/html,application/json\r\n" +
                             "Connection: close\r\n\r\n";

            PrintWriter out = new PrintWriter(socket.getOutputStream());
            out.print(request);
            out.flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = socket.getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                response.write(buffer, 0, bytesRead);
            }

            return response.toString(StandardCharsets.UTF_8.name());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String fetchHttpsUrl(String host, String path) {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, DEFAULT_HTTPS_PORT)) {
            String request = "GET " + path + " HTTP/1.1\r\n" +
                             "Host: " + host + "\r\n" +
                             "User-Agent: " + USER_AGENT + "\r\n" +
                             "Accept: text/html,application/json\r\n" +
                             "Connection: close\r\n\r\n";
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            out.print(request);
            out.flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = socket.getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                response.write(buffer, 0, bytesRead);
            }

            return response.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static URL parseUrl(String url) throws MalformedURLException {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return new URL(url);
    }

    private static String stripHtmlTags(String html) {
        return html.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void cacheResponse(String url, String content, Map<String, String> headers) {
        String cacheControl = headers.get("Cache-Control");
        String expires = headers.get("Expires");

        long expirationTime = System.currentTimeMillis() + 3600 * 1000;

        if (cacheControl != null && cacheControl.contains("max-age=")) {
            try {
                Pattern pattern = Pattern.compile("max-age=(\\d+)");
                Matcher matcher = pattern.matcher(cacheControl);
                if (matcher.find()) {
                    long maxAge = Long.parseLong(matcher.group(1));
                    expirationTime = System.currentTimeMillis() + maxAge * 1000;
                }
            } catch (Exception e) {
            }
        }
    }

    private static List<SearchResult> extractSearchResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        Pattern pattern = Pattern.compile("<h3[^>]*>(.*?)</h3>.*?<a href=\"([^\"]+)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find() && results.size() < 10) {
            String title = stripHtmlTags(matcher.group(1));
            String url = matcher.group(2);

            if (!url.startsWith("/search") && !url.startsWith("/url?")) {
                results.add(new SearchResult(title, url));
            }
        }
        return results;
    }

    private static String prettyPrintJson(String json) {
        StringBuilder prettyJson = new StringBuilder();
        int indentLevel = 0;
        boolean inQuotes = false;

        for (char c : json.toCharArray()) {
            switch (c) {
                case '{', '[':
                    prettyJson.append(c);
                    prettyJson.append("\n");
                    indentLevel++;
                    prettyJson.append(" ".repeat(indentLevel*2));
                    break;
                case '}', ']':
                    prettyJson.append('\n');
                    indentLevel--;
                    prettyJson.append(" ".repeat(indentLevel * 2));
                    prettyJson.append(c);
                    break;
                case ',':
                    prettyJson.append(c);
                    if (!inQuotes) {
                        prettyJson.append('\n');
                        prettyJson.append(" ".repeat(indentLevel * 2));
                    }
                    break;
                case ':':
                    prettyJson.append(c);
                    if (!inQuotes) {
                        prettyJson.append(' ');
                    }
                    break;
                case '"':
                    prettyJson.append(c);
                    inQuotes = !inQuotes;
                    break;
                default:
                    prettyJson.append(c);
            }
        }
        return prettyJson.toString();
    }
}
