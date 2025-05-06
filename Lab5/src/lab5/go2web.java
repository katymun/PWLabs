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
import java.net.URLDecoder;

public class go2web {
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static Map<String, CachedResponse> cache = new HashMap<>();

    public static void main(String[] args) {
        if (args.length > 0) {
            processArguments(args);
            return;
        }

        Scanner scanner = new Scanner(System.in);
        printHelp();
        while (true) {
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                printHelp();
                return;
            }

            String[] inputArgs = input.split("\\s+");
            processArguments(inputArgs);
        }
    }

    private static void processArguments(String[] args) {
        System.out.println("Args: " + Arrays.toString(args));
        try {
            if (args[0].equals("go2web")) {
                switch (args[1]) {
                    case "-u":
                        if (args.length < 3) {
                            System.out.println("Error: URL is required with -u option");
                            printHelp();
                            return;
                        }
                        System.out.println("Fetching URL: " + args[1]);
                        fetchUrl(args[2]);
                        break;
                    case "-s":
                        if (args.length < 3) {
                            System.out.println("Error: Search term is required with -s option");
                            printHelp();
                            return;
                        }

                        StringBuilder searchTerm = new StringBuilder(args[2]);
                        if (args.length >= 4) {
                            for (int i = 3; i < args.length; i++) {
                                searchTerm.append(" ").append(args[i]);
                            }
                        }
                        searchTerm(searchTerm.toString());
                        break;
                    case "-h":
                        printHelp();
                        break;
                    case "-x":
                        System.out.println("Good bye!");
                        System.exit(0);
                    default:
                        System.out.println("Unknown option: " + args[1]);
                        printHelp();
                        break;
                }
            } else {
                System.out.println("Unknown command: " + args[0]);
            }
        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void searchTerm(String searchTerm) {
        try {
            String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);

            String url = "https://html.duckduckgo.com/html/?q=" + encodedSearchTerm;

            URL parsedUrl = parseUrl(url);
            String host = parsedUrl.getHost();
            String path = parsedUrl.getPath() + "?q=" + encodedSearchTerm;

            System.out.println("Connecting to " + host + "...");
            String response = fetchHttpsUrl(host, path);

            saveResponseToFile(response, "duckduckgo_response.html");
//            System.out.println("Raw response saved to 'duckduckgo_response.html' for debugging");

//            System.out.println("Response received, processing...");
            HttpResponse httpResponse = parseHttpResponse(response);

            // Check if we need to follow a redirect
            if (httpResponse.statusCode >= 300 && httpResponse.statusCode < 400) {
                String location = httpResponse.headers.get("Location");
                if (location != null) {
                    System.out.println("Received redirect to: " + location);
                    URL redirectUrl = new URL(new URL(url), location);
                    host = redirectUrl.getHost();
                    path = redirectUrl.getPath();
                    if (redirectUrl.getQuery() != null) {
                        path += "?" + redirectUrl.getQuery();
                    }

                    System.out.println("Following redirect to " + host + path + "...");
                    response = fetchHttpsUrl(host, path);
                    saveResponseToFile(response, "duckduckgo_redirect_response.html");
                    httpResponse = parseHttpResponse(response);
                }
            }

//            System.out.println("Extracting search results...");
            List<SearchResult> searchResults = extractDuckDuckGoSearchResults(httpResponse.body);

            if (searchResults.isEmpty()) {
//                System.out.println("No search results found. Trying alternative extraction method...");
                searchResults = extractAlternativeDuckDuckGoResults(httpResponse.body);
            }

            if (searchResults.isEmpty()) {
                System.out.println("No search results found. The search engine might be blocking automated requests.");
                System.out.println("Check the saved HTML file to see the actual response.");
                return;
            }

            System.out.println("\nSearch Results for: " + searchTerm);
            System.out.println("------------------------------------");

            int count = 0;
            for (SearchResult result : searchResults) {
                if (count >= 10) break;
                System.out.println((count + 1) + ". " + result.title);
                System.out.println("   URL: " + result.url);
                System.out.println();
                count++;
            }
        } catch (Exception e) {
            System.out.println("Error searching: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void saveResponseToFile(String content, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        } catch (IOException e) {
            System.out.println("Error saving response to file: " + e.getMessage());
        }
    }

    private static List<SearchResult> extractDuckDuckGoSearchResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        Pattern pattern = Pattern.compile("<a class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find() && results.size() < 10) {
            String url = matcher.group(1);
            String title = stripHtmlTags(matcher.group(2));

            if (url.startsWith("/l/?")) {
                Pattern urlPattern = Pattern.compile("uddg=([^&]+)");
                Matcher urlMatcher = urlPattern.matcher(url);
                if (urlMatcher.find()) {
                    try {
                        url = URLDecoder.decode(urlMatcher.group(1), StandardCharsets.UTF_8.name());
                    } catch (UnsupportedEncodingException e) {
                    }
                }
            }

            if (!title.isEmpty() && !url.isEmpty()) {
                results.add(new SearchResult(title, url));
            }
        }

        return results;
    }

    private static List<SearchResult> extractAlternativeDuckDuckGoResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        Pattern pattern = Pattern.compile("<a\\s+[^>]*?class=\"[^\"]*?(?:result|link)[^\"]*?\"[^>]*?href=\"([^\"]+)\"[^>]*?>(.*?)</a>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find() && results.size() < 20) {
            String url = matcher.group(1);
            String title = stripHtmlTags(matcher.group(2)).trim();

            if (!url.contains("javascript:") && !url.contains("#") && !title.isEmpty() && title.length() > 5) {
                if (url.startsWith("/l/?")) {
                    Pattern uddgPattern = Pattern.compile("uddg=([^&]+)");
                    Matcher uddgMatcher = uddgPattern.matcher(url);
                    if (uddgMatcher.find()) {
                        try {
                            url = URLDecoder.decode(uddgMatcher.group(1), StandardCharsets.UTF_8.name());
                        } catch (Exception e) {
                        }
                    }
                }

                results.add(new SearchResult(title, url));
            }
        }

        return results;
    }

    private static void printHelp() {
        System.out.println("go2web - simple web client");
        System.out.println("Usage:");
        System.out.println("\tgo2web -u <URL>          #make an HTTP request to the specified URL and print the response");
        System.out.println("\tgo2web -s <search-term>  #make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("\tgo2web -h                #show this help");
        System.out.println("\tgo2web -x                #exit program");
    }

    private static void fetchUrl(String url) throws Exception {
        if (cache.containsKey(url) && !cache.get(url).isExpired()) {
            System.out.println("(Cached) " + url);
            System.out.println(cache.get(url).getContent());
            return;
        }

        URL parsedUrl = parseUrl(url);
        String host = parsedUrl.getHost();
        String path = parsedUrl.getPath().isEmpty() ? "/" : parsedUrl.getPath();
        String query = parsedUrl.getQuery();
        if (query != null) {
            path += "?" + query;
        }

        System.out.println("Connecting to " + host + "...");
        String response;
        if (parsedUrl.getProtocol().equals("https")) {
            response = fetchHttpsUrl(host, path);
        } else {
            response = fetchHttpUrl(host, path);
        }

        System.out.println("Response received, processing...");
        HttpResponse httpResponse = parseHttpResponse(response);

        if (httpResponse.statusCode >= 300 && httpResponse.statusCode < 400) {
            String location = httpResponse.headers.get("Location");
            if (location != null) {
                System.out.println("Redirecting to: " + location);
                fetchUrl(location);
                return;
            }
        }

        String contentType = httpResponse.headers.get("Content-Type");
        String processedContent;

        if (contentType != null && contentType.contains("application/json")) {
            processedContent = prettyPrintJson(httpResponse.body);
        } else {
            processedContent = stripHtmlTags(httpResponse.body);
        }

        cacheResponse(url, processedContent, httpResponse.headers);

        System.out.println("\nResponse from " + url + ":");
        System.out.println("------------------------------------");
        System.out.println(processedContent);
    }

    private static HttpResponse parseHttpResponse(String response) {
        HttpResponse httpResponse = new HttpResponse();
        String headersStr;
        int headerBodySeparator = response.indexOf("\r\n\r\n");
        if (headerBodySeparator == -1) {
            httpResponse.body = "";
            headersStr = response;
        } else {
            headersStr = response.substring(0, headerBodySeparator);
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
                        String name = headers[i].substring(0, colonPos).trim();
                        String value = headers[i].substring(colonPos + 1).trim();
                        httpResponse.headers.put(name, value);
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
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3\r\n" +
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
            throw new RuntimeException("Unknown host: " + host, e);
        } catch (IOException e) {
            throw new RuntimeException("IO error when connecting to " + host + ": " + e.getMessage(), e);
        }
    }

    private static String fetchHttpsUrl(String host, String path) {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, DEFAULT_HTTPS_PORT)) {
            String request = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3\r\n" +
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
            throw new RuntimeException("IO error when connecting to " + host + " via HTTPS: " + e.getMessage(), e);
        }
    }

    private static URL parseUrl(String url) throws MalformedURLException {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return new URL(url);
    }

    private static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String htmlTagPattern = "<[^>]*>";

        String result = html.replaceAll(htmlTagPattern, "");

        result = result.replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&#39;", "'");
        result = result.trim();
        return result;
    }
    private static void cacheResponse(String url, String content, Map<String, String> headers) {
        String cacheControl = headers.get("Cache-Control");
        String expires = headers.get("Expires");

        long expirationTime = System.currentTimeMillis() + 3600 * 1000; // Default: 1 hour

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

        cache.put(url, new CachedResponse(content, expirationTime));
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