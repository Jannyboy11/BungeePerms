package net.alpenblock.bungeeperms;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.alpenblock.bungeeperms.platform.PlatformPlugin;

public class Debug
{

    private Logger logger;
    private String path;
    private BPConfig config;
    private PlatformPlugin plugin;
    private boolean showexceptions;
    private boolean showlogs;

    public Debug(PlatformPlugin p, BPConfig conf, String loggername)
    {
        plugin = p;
        config = conf;
        loadconfig();
        File file = new File(path);
        try
        {
            if (!file.isFile() || !file.exists())
            {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            logger = Logger.getLogger(loggername + "Debug");
            logger.setUseParentHandlers(false);
            FileHandler fh = new FileHandler(path, true);
            fh.setFormatter(new DebugFormatter());
            logger.addHandler(fh);
        }
        catch (IOException e)
        {
            System.err.println("Failed to create debug log file " + file + "! Cause: " + e.getMessage());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void loadconfig()
    {
        path = config.getDebugPath();
        showexceptions = config.isDebugShowExceptions();
        showlogs = config.isDebugShowLogs();
    }

    public void log(String str)
    {
        File file = new File(path);
        if (!file.isFile())
        {
            try
            {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        if (str == null)
        {
            str = "null";
        }
        logger.info(str);
        if (showlogs || config.isDebug())
        {
            BungeePerms.getLogger().info("[Debug] " + str);
        }
    }

    public void log(Object o)
    {
        if (o == null)
        {
            log("null");
        }
        else
        {
            log(o.toString());
        }
    }

    public void log(Exception e)
    {
        File file = new File(path);
        if (!file.isFile())
        {
            try
            {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        logger.log(Level.SEVERE, e.getMessage(), e);
        if (showexceptions || config.isDebug())
        {
            e.printStackTrace();
        }
    }
}
