package org.minecraft.eden.Eden.mayuela.message;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlMessageService implements MessageService {

    private static final String FILE_NAME = "messages.yml";
    private static final String MESSAGE_ROOT = "messages.";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final File file;
    private final Map<MayuelaMessage, String> templates = new EnumMap<>(MayuelaMessage.class);
    private final Map<MayuelaMessage, Component> cachedComponents = new EnumMap<>(MayuelaMessage.class);
    private TagResolver colorResolver = TagResolver.empty();

    public YamlMessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    @Override
    public void reload() {
        ensureFileExists();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        colorResolver = loadColorResolver(config);
        templates.clear();
        cachedComponents.clear();
        for (MayuelaMessage message : MayuelaMessage.values()) {
            String template = config.getString(MESSAGE_ROOT + message.path());
            if (template == null || template.isBlank()) {
                template = "<danger>누락된 메시지: " + message.path();
                plugin.getLogger().warning(FILE_NAME + "에 메시지가 없습니다: " + message.path());
            }
            templates.put(message, template);
            if (message.placeholders().length == 0) {
                cachedComponents.put(message, deserialize(template));
            }
        }
    }

    @Override
    public void send(CommandSender sender, MayuelaMessage message, Object... values) {
        sender.sendMessage(component(message, values));
    }

    @Override
    public Component component(MayuelaMessage message, Object... values) {
        if (values.length == 0 && cachedComponents.containsKey(message)) {
            return cachedComponents.get(message);
        }
        String template = templates.getOrDefault(message, "<danger>누락된 메시지: " + message.path());
        TagResolver placeholders = placeholderResolver(message, values);
        return MINI_MESSAGE.deserialize(rewritePlaceholders(template, message), colorResolver, placeholders);
    }

    @Override
    public void sendComponent(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    private void ensureFileExists() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("플러그인 데이터 폴더를 생성하지 못했습니다: " + plugin.getDataFolder());
        }
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
    }

    private TagResolver loadColorResolver(YamlConfiguration config) {
        TagResolver.Builder builder = TagResolver.builder();
        for (MayuelaColor color : MayuelaColor.values()) {
            String hex = config.getString("colors." + color.key(), color.hex());
            TextColor textColor = TextColor.fromHexString(hex);
            if (textColor == null) {
                plugin.getLogger().warning("잘못된 메시지 색상 HEX입니다: colors." + color.key() + "=" + hex);
                textColor = TextColor.fromHexString(color.hex());
            }
            builder.resolver(TagResolver.resolver(color.key(), Tag.styling(textColor)));
        }
        return builder.build();
    }

    private Component deserialize(String template) {
        return MINI_MESSAGE.deserialize(rewritePlaceholders(template, null), colorResolver);
    }

    private TagResolver placeholderResolver(MayuelaMessage message, Object... values) {
        TagResolver.Builder builder = TagResolver.builder();
        String[] names = message.placeholders();
        for (int index = 0; index < names.length; index++) {
            Object value = index < values.length ? values[index] : "";
            builder.resolver(Placeholder.unparsed(names[index], String.valueOf(value)));
        }
        if (values.length != names.length) {
            plugin.getLogger().log(Level.FINE, "메시지 placeholder 개수가 맞지 않습니다: {0}", message.path());
        }
        return builder.build();
    }

    private String rewritePlaceholders(String template, MayuelaMessage message) {
        if (message == null) {
            return template;
        }
        String rewritten = template;
        for (String placeholder : message.placeholders()) {
            rewritten = rewritten.replace("{" + placeholder + "}", "<" + placeholder + ">");
        }
        return rewritten;
    }
}
