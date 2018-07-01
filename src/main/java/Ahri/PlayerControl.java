package Ahri;


import com.jagrosh.jdautilities.menu.OrderedMenu;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.GenericMessageEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlayerControl extends ListenerAdapter {


    private static AudioPlayerManager playerManager = null;
    private static Map<Long, GuildMusicManager> musicManagers = null;

    public static boolean isConnected(Guild guild) { return guild.getAudioManager().isConnected(); }


    public static void startAudio() {
        musicManagers = new HashMap<>();

        playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String[] command = event.getMessage().getContentRaw().split(" ", 2);
        Guild guild = event.getGuild();

        if (guild != null) {
            if ("!play".equals(command[0]) && command.length == 2) {
                loadAndPlay(event.getTextChannel(), command[1], event, guild);
            } else if ("!skip".equals(command[0])) {
                skipTrack(event.getTextChannel());
            }
            else if ("!leave".equals(command[0]))
            {
                guild.getAudioManager().setSendingHandler(null);
                guild.getAudioManager().closeAudioConnection();
            }
            else if ("!stop".equals(command[0]))
            {
                if (PlayerControl.isConnected(guild)) {
                GuildMusicManager musicManager = getGuildAudioPlayer(guild);
                List<AudioTrack> queue = new ArrayList<>(musicManager.scheduler.getQueue());
                int queueSize = queue.size() + 1;
                musicManager.scheduler.emptyQueue();
                musicManager.scheduler.nextTrack();

                event.getTextChannel().sendMessage("Removed " + queueSize + " songs from the queue").queue();
                guild.getAudioManager().closeAudioConnection();
            }
                else {
                    event.getTextChannel().sendMessage("Currently not playing or connected to a voice channel").queue();
                }
            }

        }

        super.onMessageReceived(event);
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl, MessageReceivedEvent event, Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

                play(channel.getGuild(), musicManager, track, event);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {

                AudioTrack firstTrack = playlist.getSelectedTrack();
                if (trackUrl.startsWith("ytsearch:")) {
                    List<AudioTrack> trackList = playlist.getTracks().subList(0, 5);

                    OrderedMenu.Builder menu = new OrderedMenu.Builder();
                    menu.setUsers(event.getAuthor());
                    menu.useCancelButton(true);
                    menu.setColor(Color.PINK);
                    menu.setTimeout(60, TimeUnit.SECONDS);
                    menu.setText("Choose which Track you want to play");

                    trackList.forEach(audioTrack -> menu.addChoice(audioTrack.getInfo().title));

                    menu.setEventWaiter(Ahri.getEventWaiter());
                    menu.setSelection((msg, i) -> {
                        AudioTrack track = trackList.get(i - 1);
                        musicManager.scheduler.queue(track);
                        msg.getTextChannel().sendMessage("Adding to queue: " + track.getInfo().title).queue();
                        connecttoVoiceChannel(guild.getAudioManager(), event);
                    });

                    menu.setCancel((msg) -> msg.getTextChannel().sendMessage("Track Selection canceled").queue());
                    menu.build().display(event.getChannel());
                }
                else
                {
                    channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();
                    TrackScheduler.queue(firstTrack);
                }
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, MessageReceivedEvent event) {

        connecttoVoiceChannel(guild.getAudioManager(), event);

        musicManager.scheduler.queue(track);
    }



    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("Skipped to next track.").queue();
    }

    private void connecttoVoiceChannel(AudioManager audioManager, MessageReceivedEvent event) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            if (event.getMember().getVoiceState().inVoiceChannel()) {
                audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
            }
        }
    }
}