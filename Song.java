import java.util.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Song {
    int songId;

    @JsonCreator
    public Song(@JsonProperty("songId") int songId) {
        this.songId = songId;
    }

    public int getSongId() { return this.songId; }
    public void setSongId(int songId) { this.songId = songId; }
}
