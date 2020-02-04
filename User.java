import java.util.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class User {
    int userId;
    List<Playlist> playlists = new ArrayList<Playlist>();

    public User(int userId) {
        this.userId = userId;
    }

    @JsonCreator
    public User(@JsonProperty("userId") int userId, @JsonProperty("playlists") List<Playlist> playlists) {
        this.userId = userId;
        this.playlists = playlists;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public List<Playlist> getPlaylists() { return this.playlists; }
    public void setPlaylists(List<Playlist> playlists) { this.playlists = playlists; }
    

    void addPlaylist(Playlist playlist) {
        this.playlists.add(playlist);
    }

    void removePlaylist(int playlistId) {
        boolean found = false;
        int i=0; 
        while (i<playlists.size()) {
            if (playlists.get(i).playlistId == playlistId) {
                found = true;
                break;
            }
            i++;
        }

        if (found) {
            playlists.remove(i);
        }
    }
}
