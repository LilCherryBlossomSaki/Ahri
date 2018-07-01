package Ahri;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import com.google.common.base.CharMatcher;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.Map;


public class Ahri {

    private static EventWaiter eventWaiter;



    public static void main(String[] arguments) throws Exception
    {
        eventWaiter = new EventWaiter();



        PlayerControl.startAudio();

        Yaml yaml = new Yaml();
        String configFile = FileUtils.readFileToString(loadFile("config"), "UTF-8");
        configFile = removeTabs(configFile);
        Map<String, Object> config = (Map<String, Object>) yaml.load(configFile);
        config.keySet().forEach((String key) -> config.putIfAbsent(key, ""));



        JDA api = new JDABuilder(AccountType.BOT)
                    .setToken(configFile)
                    .addEventListener(new MyListener())
                    .addEventListener(new PlayerControl())
                    .addEventListener(eventWaiter)
                    .buildAsync();

    }


    public static EventWaiter getEventWaiter() {
        return eventWaiter;
    }

    private static File loadFile(String name) {
        String path = "./" + name + ".yaml";
        return new File(path);
    }

    // Remove tab characters and replace them with spaces
    private static String removeTabs(String content) {
        CharMatcher tab = CharMatcher.is('\t');
        if(tab.matchesAnyOf(content)) {
            return tab.replaceFrom(content, "  ");
        } else {
            return content;
        }
    }




}

















