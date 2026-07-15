package emanondev.itemedit.compability;

import emanondev.itemedit.ParsedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class NexoItemProvider implements Listener {

    private static final String NEXO_ITEMS_CLASS = "com.nexomc.nexo.api.NexoItems";
    private static final String NEXO_ITEMS_LOADED_EVENT = "com.nexomc.nexo.api.events.NexoItemsLoadedEvent";
    private static final String ITEMEDIT_RENAME_KEY = "itemedit:rename";
    private static final String ITEMEDIT_LORE_KEY = "itemedit:lore";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static boolean updateCallbackRegistered = false;

    public NexoItemProvider() {
    }

    public static void setup() {
        registerUpdateCallback();
    }

    @EventHandler
    public void onNexoItemsLoaded(Event event) {
        if (NEXO_ITEMS_LOADED_EVENT.equals(event.getClass().getName())) {
            registerUpdateCallback();
        }
    }

    @Nullable
    public static ItemStack getItem(@NotNull String id) {
        try {
            Object itemBuilder = getItemBuilder(id);
            if (itemBuilder == null) {
                return null;
            }
            Method build = itemBuilder.getClass().getMethod("build");
            build.setAccessible(true);
            Object item = build.invoke(itemBuilder);
            if (item instanceof ItemStack) {
                return (ItemStack) item;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    public static String getItemName(@NotNull String id) {
        try {
            Object itemBuilder = getItemBuilder(id);
            if (itemBuilder == null) {
                return null;
            }
            Method getItemName = itemBuilder.getClass().getMethod("getItemName");
            getItemName.setAccessible(true);
            Object itemName = getItemName.invoke(itemBuilder);
            if (itemName instanceof Component) {
                return LEGACY_SERIALIZER.serialize((Component) itemName);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    public static String getId(@NotNull ItemStack item) {
        try {
            Class<?> nexoItems = Class.forName(NEXO_ITEMS_CLASS);
            Method idFromItem = nexoItems.getMethod("idFromItem", ItemStack.class);
            Object id = idFromItem.invoke(null, item);
            return id instanceof String ? (String) id : null;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void setOriginalItemRename(@NotNull ItemStack item, @Nullable String name) {
        setOriginalItemRenamePdc(item, name);
    }

    public static boolean isNexoItem(@NotNull ItemStack item) {
        return getId(item) != null;
    }

    public static boolean shouldSyncOriginalItemRename(@NotNull ItemStack item) {
        return Hooks.isNexoEnabled() || hasOriginalItemRename(item) || isNexoItem(item);
    }

    public static boolean shouldSyncItemOverrides(@NotNull ItemStack item) {
        return Hooks.isNexoEnabled() || isNexoItem(item) || readRenameOverride(item) != null || readLoreOverride(item) != null;
    }

    @NotNull
    public static ItemStack storeRenameOverride(@NotNull ItemStack item, @Nullable String name, boolean syncNexo) {
        try {
            ParsedItem parsed = new ParsedItem(item);
            if (name == null) {
                parsed.remove("custom_data", ITEMEDIT_RENAME_KEY);
            } else {
                parsed.set(name, "custom_data", ITEMEDIT_RENAME_KEY);
            }
            item = parsed.toItemStack();
        } catch (Throwable ignored) {
        }
        return applyOriginalItemRename(item, name, syncNexo);
    }

    @NotNull
    public static ItemStack storeLoreOverride(@NotNull ItemStack item, @Nullable List<String> lore, boolean syncNexo) {
        try {
            ParsedItem parsed = new ParsedItem(item);
            if (lore == null) {
                parsed.remove("custom_data", ITEMEDIT_LORE_KEY);
            } else {
                parsed.set(encodeLore(lore), "custom_data", ITEMEDIT_LORE_KEY);
            }
            item = parsed.toItemStack();
        } catch (Throwable ignored) {
        }
        if (syncNexo) {
            applyLore(item, lore);
        }
        return item;
    }

    @NotNull
    public static ItemStack applyOriginalItemRename(@NotNull ItemStack item, @Nullable String name) {
        return applyOriginalItemRename(item, name, false);
    }

    @NotNull
    public static ItemStack applyOriginalItemRename(@NotNull ItemStack item, @Nullable String name, boolean force) {
        ItemStack nexoRenamed = applyItemName(item, name);
        if (nexoRenamed != null) {
            setOriginalItemRenamePdc(nexoRenamed, name);
            return nexoRenamed;
        }
        try {
            ParsedItem parsed = new ParsedItem(item);
            parsed.remove("custom_data", "nexooriginal_item_rename");
            if (name == null) {
                parsed.remove("custom_data", "nexo:original_item_rename");
            } else if (force || parsed.readMap("custom_data") != null || getId(item) != null) {
                parsed.set(name, "custom_data", "nexo:original_item_rename");
            }
            ItemStack result = parsed.toItemStack();
            applyDisplayName(result, name);
            setOriginalItemRenamePdc(result, name);
            return result;
        } catch (Throwable ignored) {
            applyDisplayName(item, name);
            setOriginalItemRenamePdc(item, name);
            return item;
        }
    }

    @Nullable
    private static ItemStack applyItemName(@NotNull ItemStack item, @Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            Class<?> nexoItems = Class.forName(NEXO_ITEMS_CLASS);
            Method builderFromItem = nexoItems.getMethod("builderFromItem", ItemStack.class);
            Object itemBuilder = builderFromItem.invoke(null, item);
            if (itemBuilder == null) {
                return null;
            }

            Component component = LEGACY_SERIALIZER.deserialize(name);
            Method itemName = itemBuilder.getClass().getMethod("itemName", Component.class);
            itemName.setAccessible(true);
            Object resultBuilder = itemName.invoke(itemBuilder, component);
            if (resultBuilder == null) {
                resultBuilder = itemBuilder;
            }

            Method build = resultBuilder.getClass().getMethod("build");
            build.setAccessible(true);
            Object result = build.invoke(resultBuilder);
            if (result instanceof ItemStack) {
                ItemStack resultItem = (ItemStack) result;
                applyDisplayName(resultItem, name);
                return resultItem;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void applyDisplayName(@NotNull ItemStack item, @Nullable String name) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
        }
    }

    private static void setOriginalItemRenamePdc(@NotNull ItemStack item, @Nullable String name) {
        try {
            if (!item.hasItemMeta()) {
                return;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            Object data = meta.getClass().getMethod("getPersistentDataContainer").invoke(meta);
            Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
            Class<?> dataContainerClass = Class.forName("org.bukkit.persistence.PersistentDataContainer");
            Class<?> dataTypeClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            Object key = keyClass.getConstructor(String.class, String.class).newInstance("nexo", "original_item_rename");
            Object stringType = dataTypeClass.getField("STRING").get(null);
            if (name == null) {
                dataContainerClass.getMethod("remove", keyClass).invoke(data, key);
            } else if ((Boolean) dataContainerClass.getMethod("has", keyClass, dataTypeClass).invoke(data, key, stringType)
                    || getId(item) != null) {
                dataContainerClass.getMethod("set", keyClass, dataTypeClass, Object.class).invoke(data, key, stringType, name);
            }
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasOriginalItemRename(@NotNull ItemStack item) {
        try {
            ParsedItem parsed = new ParsedItem(item);
            return parsed.readString(null, "custom_data", "nexo:original_item_rename") != null
                    || parsed.readString(null, "custom_data", "nexooriginal_item_rename") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NotNull
    private static ItemStack applyStoredOverrides(@NotNull ItemStack source, @NotNull ItemStack target) {
        String rename = readRenameOverride(source);
        List<String> lore = readLoreOverride(source);
        if (rename != null) {
            target = applyOriginalItemRename(target, rename, true);
        }
        if (lore != null) {
            applyLore(target, lore);
            target = storeLoreOverride(target, lore, false);
        }
        if (rename != null) {
            target = storeRenameOverride(target, rename, false);
        }
        return target;
    }

    @Nullable
    private static String readRenameOverride(@NotNull ItemStack item) {
        try {
            return new ParsedItem(item).readString(null, "custom_data", ITEMEDIT_RENAME_KEY);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static List<String> readLoreOverride(@NotNull ItemStack item) {
        try {
            String raw = new ParsedItem(item).readString(null, "custom_data", ITEMEDIT_LORE_KEY);
            return raw == null ? null : decodeLore(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyLore(@NotNull ItemStack item, @Nullable List<String> lore) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
        }
    }

    private static String encodeLore(@NotNull List<String> lore) {
        List<String> encoded = new ArrayList<>();
        for (String line : lore) {
            encoded.add(Base64.getEncoder().encodeToString((line == null ? "" : line).getBytes(StandardCharsets.UTF_8)));
        }
        return String.join(",", encoded);
    }

    @NotNull
    private static List<String> decodeLore(@NotNull String raw) {
        List<String> lore = new ArrayList<>();
        if (raw.isEmpty()) {
            return lore;
        }
        for (String line : raw.split(",", -1)) {
            lore.add(new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8));
        }
        return lore;
    }

    private static void registerUpdateCallback() {
        if (updateCallbackRegistered || !Hooks.isNexoEnabled()) {
            return;
        }
        try {
            Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");
            Object key = keyClass.getMethod("key", String.class, String.class).invoke(null, "itemedit", "item_overrides");
            Class<?> nexoItems = Class.forName(NEXO_ITEMS_CLASS);

            for (Method method : nexoItems.getMethods()) {
                if (!method.getName().equals("registerUpdateCallback") || method.getParameterTypes().length != 3) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                Object preUpdate = proxy(params[1], (proxy, invoked, args) -> {
                    if (invoked.getName().equals("apply") && args != null && args.length >= 1) {
                        return args[0];
                    }
                    return handleObjectMethod(proxy, invoked, args);
                });
                Object postUpdate = proxy(params[2], (proxy, invoked, args) -> {
                    if (invoked.getName().equals("apply") && args != null && args.length >= 3
                            && args[1] instanceof ItemStack && args[2] instanceof ItemStack) {
                        return applyStoredOverrides((ItemStack) args[2], (ItemStack) args[1]);
                    }
                    return handleObjectMethod(proxy, invoked, args);
                });
                method.invoke(null, key, preUpdate, postUpdate);
                updateCallbackRegistered = true;
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    @Nullable
    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return "ItemEdit Nexo update callback";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                return null;
        }
    }

    @Nullable
    private static Object getItemBuilder(@NotNull String id) throws ReflectiveOperationException {
        Class<?> nexoItems = Class.forName(NEXO_ITEMS_CLASS);
        Method itemFromId = nexoItems.getMethod("itemFromId", String.class);
        return itemFromId.invoke(null, id);
    }
}
