package dev.pgm.community.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * A {@link ResourceBundle.Control} implementation which allows reading of .properties files encoded
 * with UTF-8.
 *
 * <p>See https://stackoverflow.com/a/4660195 for more details.
 */
final class UTF8Control extends ResourceBundle.Control {

  /** {@inheritDoc} */
  public ResourceBundle newBundle(
      String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
      throws IOException {
    // The below is a copy of the default implementation.
    String bundleName = toBundleName(baseName, locale);
    String resourceName = toResourceName(bundleName, "properties");
    ResourceBundle bundle = null;
    InputStream stream = null;
    if (reload) {
      URL url = loader.getResource(resourceName);
      if (url != null) {
        URLConnection connection = url.openConnection();
        if (connection != null) {
          connection.setUseCaches(false);
          stream = connection.getInputStream();
        }
      }
    } else {
      stream = loader.getResourceAsStream(resourceName);
    }
    if (stream != null) {
      try {
        // Only this line is changed to make it to read properties files as UTF-8.
        bundle = new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
      } finally {
        stream.close();
      }
    }
    return bundle;
  }

  @Override
  public List<Locale> getCandidateLocales(String name, Locale locale) {
    return Arrays.asList(Locale.ROOT);
  }
}
