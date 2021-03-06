package de.cubenation.plugins.cnherochattransporter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.dthielke.herochat.ChannelChatEvent;
import com.dthielke.herochat.ChannelManager;
import com.dthielke.herochat.Herochat;
import com.dthielke.herochat.Chatter.Result;
import com.frdfsnlght.transporter.api.API;
import com.frdfsnlght.transporter.api.RemoteServer;
import com.frdfsnlght.transporter.api.TypeMap;
import com.frdfsnlght.transporter.api.event.RemoteRequestReceivedEvent;

public class CNHeroChatTransporter extends JavaPlugin {
	
	private API _transporterAPI;
	private ChannelManager _channelManager;

    public void onEnable() {
        // Save a copy of the default config.yml if one is not there
        this.saveDefaultConfig();

		// read initial config if there's any
		FileConfiguration config = getConfig();
		config.options().copyDefaults(true);
		
		// add defaults if there are new ones
		YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(getResource("defaultConfig.yml"));
		config.setDefaults(defaultConfig);
		saveConfig();
		
		// load config settings
		reloadConfiguration();
    	
        /* Register our listener */
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
    }
    
	public void reloadConfiguration() {
		reloadConfig();
	}

    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && (!(sender instanceof Player) || sender.hasPermission("CNHeroChatTransporter.restart"))) {
        	reloadConfig();
        	sender.sendMessage("[HCT] Config reloaded");
        }
        return true;
    }
	
    public ChannelManager herochatCM() {
    	if (_channelManager == null) {
            /* Get Herochat */
            Plugin p = getServer().getPluginManager().getPlugin("Herochat");
            if(p == null) {
                getLogger().severe("Cannot find Herochat! Disabling...");
                setEnabled(false);
                return null;
            }
            
            _channelManager = Herochat.getChannelManager();
            if(_channelManager == null) {
            	getLogger().severe("Cannot find Herochat channel manager. Disabling...");
                setEnabled(false);
                return null;
            }
    	}
    	return _channelManager;
    }
    
    public API transporterAPI() {
    	if (_transporterAPI == null) {
    		Plugin transporter = getServer().getPluginManager().getPlugin("Transporter");
    		if (transporter == null || !transporter.isEnabled()) {
                getLogger().severe("Cannot find Transporter! Disabling...");
                setEnabled(false);
                return null;
    		}

    		_transporterAPI = ((com.frdfsnlght.transporter.Transporter)transporter).getAPI();
            if(_transporterAPI == null) {
            	getLogger().severe("Cannot find Transporter API. Disabling...");
                setEnabled(false);
                return null;
            }

    	}
    	return _transporterAPI;
    }
    
    private class ChatListener implements Listener {
    	
        @EventHandler(priority=EventPriority.MONITOR)
        public void onHeroChatMessage(ChannelChatEvent event) {            
            if (!event.getChannel().getName().equalsIgnoreCase("global")) {
                return;
            }
            if(event.getResult() != Result.ALLOWED) {
                return;
            }
            
        	String cname = event.getChannel().getName();
            String pname = event.getSender().getName();
            String message = event.getMessage();

    	    for (RemoteServer s : transporterAPI().getRemoteServers().toArray(new RemoteServer[0])) {

    	        // check if user listens to the remote servers channel, otherwise don't send to this server
                String remotesChannelName = getConfig().getString("incoming." + s.getName() + ".target");
                if (remotesChannelName == null) {
                    getLogger().warning("Channel target for '" + s.getName() + "' coult not be found?!");
                    continue;
                }
                
                if (!herochatCM().hasChannel(remotesChannelName)) {
                    getLogger().warning("Channel '" + remotesChannelName + "' coult not be found on this server?!");
                    continue;
                }

    	        if (!herochatCM().getChannel(remotesChannelName).getMembers().contains(event.getSender())) {
                    continue;
    	        }
    	        
    	    	TypeMap payload = new TypeMap();
    	    	payload.set("channel", cname);
    	    	payload.set("sender", pname);
    	    	payload.set("message", message);
				
    	    	s.sendRemoteRequest(null, payload);    	    	
    	    }
    	    
        }
        
        @EventHandler
        public void onRemoteRequestReceived(RemoteRequestReceivedEvent event) {
        	TypeMap payload = event.getRequest();
        	String pname = payload.getString("sender");
        	String cname = payload.getString("channel");
        	String message = payload.getString("message");
        	String remoteServerName = event.getRemoteServer().getName();
        	
        	ConfigurationSection config = getConfig().getConfigurationSection("incoming." + remoteServerName);
        	if (config == null) {
        		getLogger().warning("Found incoming chat transmission from '"+remoteServerName+"' for channel '"+cname+"', but no destination set.");
        		return;
        	}
        	
        	String targetChannelName = config.getString("target");
        	if (targetChannelName == null) {
                getLogger().warning("Found incoming chat transmission from '"+remoteServerName+"' for channel '"+cname+"', but no target set.");
                return;
            }
        	
            if (!herochatCM().hasChannel(targetChannelName)) {
                getLogger().warning("Targetchannel '"+targetChannelName+"' not found.");
                return;
            }
        	        	
        	String format = config.getString("format");
        	if (format == null) {
        		format = "[%sender%@%source%] %message%";
        	}
        	
        	String output = ChatColor.translateAlternateColorCodes('&', format
        			.replace("%sender%", pname)
        			.replace("%sourceChannel%", cname)
        			.replace("%sourceServer%", remoteServerName)
        			.replace("%message%", message));
        	
        	herochatCM().getChannel(targetChannelName).announce(output);
        }
    }
	

}
