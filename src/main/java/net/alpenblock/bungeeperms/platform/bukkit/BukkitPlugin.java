package net.alpenblock.bungeeperms.platform.bukkit;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.SneakyThrows;
import net.alpenblock.bungeeperms.BungeePerms;
import net.alpenblock.bungeeperms.Color;
import net.alpenblock.bungeeperms.Config;
import net.alpenblock.bungeeperms.Statics;
import net.alpenblock.bungeeperms.platform.MessageEncoder;
import net.alpenblock.bungeeperms.platform.bukkit.bridge.BridgeManager;
import net.alpenblock.bungeeperms.platform.Sender;
import net.alpenblock.bungeeperms.platform.PlatformPlugin;
import net.alpenblock.bungeeperms.platform.PlatformType;
import net.alpenblock.bungeeperms.platform.PluginMessageSender;
import net.alpenblock.bungeeperms.platform.independend.GroupProcessor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class BukkitPlugin extends JavaPlugin implements PlatformPlugin
{

    private static final double MILLI2TICK = 20F / 1000;

    @Getter
    private static BukkitPlugin instance;

    private BukkitConfig conf;

    //platform dependend parts
    private BukkitEventListener listener;
    private BukkitEventDispatcher dispatcher;
    private BukkitNotifier notifier;
    private PluginMessageSender pmsender;

    private BungeePerms bungeeperms;

    private final PlatformType platformType = PlatformType.Bukkit;

    //platform extra things
    @Getter
    private BridgeManager bridge;

    @Override
    public void onLoad()
    {
        //static
        instance = this;

        //metrics
        startMetrics();

        //load config
        Config config = new Config(this, "/config.yml");
        config.load();
        conf = new BukkitConfig(config);
        conf.load();

        //register commands
        loadcmds();

        listener = new BukkitEventListener(conf);
        dispatcher = new BukkitEventDispatcher();
        notifier = new BukkitNotifier(conf);
        pmsender = new BukkitPluginMessageSender();

        bungeeperms = new BungeePerms(this, conf, pmsender, notifier, listener, dispatcher);
        bungeeperms.load();

        //extra part
        bridge = new BridgeManager();
        bridge.load();
        bungeeperms.getPermissionsResolver().registerProcessor(new GroupProcessor());
        bungeeperms.getPermissionsResolver().registerProcessor(new SuperPermsPreProcessor());
    }

    @Override
    public void onEnable()
    {
        Bukkit.getMessenger().registerIncomingPluginChannel(this, BungeePerms.CHANNEL, listener);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, BungeePerms.CHANNEL);
        bungeeperms.enable();
        bridge.enable();
    }

    @Override
    public void onDisable()
    {
        bridge.disable();
        bungeeperms.disable();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, BungeePerms.CHANNEL, listener);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, BungeePerms.CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        return bungeeperms.getCommandHandler().onCommand(new BukkitSender(sender), cmd.getName(), label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args)
    {
        List<String> l = new ArrayList<>();
        if (!conf.isTabComplete() || args.length == 0)
            return l;

        for (Player p : getBukkitPlayers())
        {
            if (Statics.toLower(p.getName()).startsWith(Statics.toLower(args[args.length - 1])))
            {
                l.add(p.getName());
            }
        }

        return l;
    }

    private void loadcmds()
    {
        Command command = new Command("bungeeperms")
        {
            @Override
            public boolean execute(final CommandSender sender, final String alias, final String[] args)
            {
                final Command cmd = this;
                Runnable r = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (!BukkitPlugin.this.onCommand(sender, cmd, alias, args))
                        {
                            sender.sendMessage(Color.Error + "[BungeePerms] Command not found");
                        }
                    }
                };
                if (conf.isAsyncCommands())
                {
                    Bukkit.getScheduler().runTaskAsynchronously(instance, r);
                }
                else
                {
                    r.run();
                }
                return true;
            }
        };

        command.setAliases(Arrays.asList("bp"));
        command.setPermission(null);

        getCommandMap().register("bungeeperms", command);

    }

    private CommandMap getCommandMap()
    {
        try
        {
            Field f = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getPluginManager());
        }
        catch (Exception ex)
        {
        }
        return null;
    }

//plugin info
    @Override
    public String getPluginName()
    {
        return this.getDescription().getName();
    }

    @Override
    public String getVersion()
    {
        return this.getDescription().getVersion();
    }

    @Override
    public String getAuthor()
    {
        return this.getDescription().getAuthors().get(0);
    }

    @Override
    public String getPluginFolderPath()
    {
        return this.getDataFolder().getAbsolutePath();
    }

    @Override
    public File getPluginFolder()
    {
        return this.getDataFolder();
    }

    @Override
    public Sender getPlayer(String name)
    {
        CommandSender sender = Bukkit.getPlayer(name);

        Sender s = null;

        if (sender != null)
        {
            s = new BukkitSender(sender);
        }

        return s;
    }

    @Override
    public Sender getPlayer(UUID uuid)
    {
        CommandSender sender = Bukkit.getPlayer(uuid);

        Sender s = null;

        if (sender != null)
        {
            s = new BukkitSender(sender);
        }

        return s;
    }

    @Override
    public Sender getConsole()
    {
        return new BukkitSender(Bukkit.getConsoleSender());
    }

    @Override
    public List<Sender> getPlayers()
    {
        List<Sender> senders = new ArrayList<>();

        for (Player pp : getBukkitPlayers())
        {
            senders.add(new BukkitSender(pp));
        }

        return senders;
    }

    @Override
    public boolean isChatApiPresent()
    {
        try
        {
            Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Override
    public MessageEncoder newMessageEncoder()
    {
        return new BukkitMessageEncoder("");
    }

    @Override
    public int registerRepeatingTask(Runnable r, long delay, long interval)
    {
        return getServer().getScheduler().runTaskTimer(this, r, (long) (delay * MILLI2TICK), (long) (interval * MILLI2TICK)).getTaskId();
    }

    @Override
    public int runTaskLater(Runnable r, long delay)
    {
        return getServer().getScheduler().runTaskLater(this, r, (long) (delay * MILLI2TICK)).getTaskId();
    }
    
    @Override
    public int runTaskLaterAsync(Runnable r, long delay)
    {
        return getServer().getScheduler().runTaskLaterAsynchronously(this, r, (long) (delay * MILLI2TICK)).getTaskId();
    }

    @Override
    public void cancelTask(int id)
    {
        getServer().getScheduler().cancelTask(id);
    }

    //for compat
    @SneakyThrows
    public static List<Player> getBukkitPlayers()
    {
        Method method = Bukkit.class.getDeclaredMethod("getOnlinePlayers");
        method.setAccessible(true);
        if (method.getReturnType() == Player[].class)
            return new ArrayList(Arrays.asList((Player[]) method.invoke(null)));
        else
            return new ArrayList((Collection) method.invoke(null));
    }

    @Override
    public Integer getBuild()
    {
        return Statics.getBuild(this);
    }

    private void startMetrics()
    {
        try
        {
            Class c = Class.forName("net.alpenblock.bungeeperms.metrics.bukkit.Metrics");
            Constructor cc = c.getConstructor(Plugin.class);
            cc.newInstance(this);
        }
        catch (Exception ex)
        {
            getLogger().severe("Could not start metrics!");
        }
    }
}
