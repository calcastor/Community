package dev.pgm.community.info;

import static net.kyori.adventure.text.Component.text;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import tc.oc.pgm.util.Audience;
import tc.oc.pgm.util.text.TextException;
import tc.oc.pgm.util.text.TextParser;

public class InfoCommandData {

  private static final String LINES_KEY = "lines";
  private static final String PERMISSION_KEY = "permission";

  private String name;
  private List<Component> lines;
  private String permission;

  public InfoCommandData(String name, List<Component> lines, String permission) {
    this.name = name;
    this.lines = lines;
    this.permission = permission;
  }

  public static InfoCommandData of(ConfigurationSection section) {
    return new InfoCommandData(
        section.getName(),
        section.getStringList(LINES_KEY).stream()
            .map(line -> {
              try {
                Component parsedComponent = TextParser.parseComponent(line);
                return addUrlEventsToComponent(parsedComponent);
              } catch (TextException e) {
                e.printStackTrace();
                return Component.text(line); // Fallback if error
              }
            })
            .collect(Collectors.toList()),
        section.getString(PERMISSION_KEY));
  }

  private static final Pattern URL_PATTERN = Pattern.compile(
      "(https?://[\\w\\-\\.]+(:\\d+)?(/[\\w\\-\\./?%&=]*)?)", Pattern.CASE_INSENSITIVE);

  private static Component addUrlEventsToComponent(Component component) {
    return component.replaceText(
        builder -> builder.match(URL_PATTERN).replacement((matchResult, textComponentBuilder) -> {
          String url = matchResult.group();
          return Component.text(url)
              .color(NamedTextColor.BLUE)
              .hoverEvent(HoverEvent.showText(Component.text()
                  .append(Component.text("Click to open ", NamedTextColor.GRAY))
                  .append(Component.text(url, NamedTextColor.BLUE))))
              .clickEvent(ClickEvent.openUrl(url));
        }));
  }

  public String getName() {
    return name;
  }

  public List<Component> getLines() {
    return lines;
  }

  public String getPermission() {
    return permission;
  }

  public void sendCommand(CommandSender sender) {
    Audience viewer = Audience.get(sender);

    if (getPermission() != null && !getPermission().isEmpty()) {
      if (!sender.hasPermission(getPermission())) {
        viewer.sendWarning(text("You do not have permission for this command"));
        return; // TODO: Translate
      }
    }

    getLines().forEach(viewer::sendMessage);
  }
}
