package lab5;

import java.net.MalformedURLException;
import java.net.URL;

public class Utils {
    public static final int DEFAULT_HTTP_PORT = 80;
    public static final int DEFAULT_HTTPS_PORT = 443;

    public static URL parseUrl(String url) throws MalformedURLException {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return new URL(url);
    }

}
