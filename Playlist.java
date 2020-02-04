import java.util.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Playlist {
    int playlistId;
    List<Song> songs = new ArrayList<Song>();

    Playlist(int playlistId) {
        this.playlistId = playlistId;
    }

    @JsonCreator
    public Playlist(@JsonProperty("playlistId") int playlistId, @JsonProperty("songs") List<Song> songs) {
        this.playlistId = playlistId;
        this.songs = songs;
    }

    public int getPlaylistId() { return this.playlistId; }
    public void setPlaylistId(int playlistId) { this.playlistId = playlistId; }
    public List<Song> getSongs() { return this.songs; }
    public void setSonds(List<Song> songs) { this.songs = songs; }

    void addSong(Song song) {
        this.songs.add(song);
    }

    void removeSong(int songId) {
        boolean found = false;
        int i=0;
        while (i<songs.size()) {
            if (songs.get(i).songId == songId) {
                found = true;
                break;
            }
            i++;
        }

        if (found) {
            songs.remove(i);
        }

    }
}
