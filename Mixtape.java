import java.io.*;
import java.util.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Mixtape {
    File dataFile;
    ObjectMapper objectMapper;
    JsonFactory jsonFactory;
    static HashMap<MixtapeAction,String> actionFormats = new HashMap<MixtapeAction,String>();

    static {
        actionFormats.put(MixtapeAction.ADD_SONG, "userId=%d/playlistId=%d/songId=%d/action=ADD_SONG");
        actionFormats.put(MixtapeAction.ADD_PLAYLIST, "userId=%d/playlistId=%d/songId=%d/action=ADD_PLAYLIST");
        actionFormats.put(MixtapeAction.REMOVE_PLAYLIST, "userId=%d/playlistId=%d/action=REMOVE_PLAYLIST");
    }
    
    public Mixtape(File dataFile) {
        this.dataFile = dataFile;
        objectMapper = new ObjectMapper();
        jsonFactory = objectMapper.getFactory();
    }

    // returns name of file storing the updated information
    public String update(File changesFile) throws Exception {
        // load change actions and hash by userid for easy retrieval
        HashMap<Integer,List<Action>> actionsByUserId = parseChanges(changesFile);

        // prepare an output stream to serialize changed data; it will be named as the same as the input,
        // but with a timestamp
        String outputFileName = deriveOutputFilename(changesFile);
        FileWriter fw = new FileWriter(new File(outputFileName));
        // we will serialize each user object, just need to add the array boundaries
        fw.write("[\n");

        // read the existing user data via a stream; parse as you go to avoid loading it all in memory        
        FileInputStream in = new FileInputStream(dataFile);
        JsonParser parser = jsonFactory.createJsonParser(in);

        // the stream is a list of User objects, each with an id;
        // parse the id and check if there are any requested changes
        // apply changes; then serialize to the output stream

        // the format is array of users; each user has an array of playlists; each playlist has an array of songs
        // ASSUME json serialzation is done in order: for users, userid then playlists;
        // for playlists, playlistId then songs
        User user = null;
        int previousUserId = -1;
        Playlist playlist = null;
        Song song = null;
        int endArrayCtr = 0;
        
        JsonToken token = null;
        while ((token = parser.nextToken()) != null) {
            if (token == JsonToken.VALUE_NUMBER_INT && parser.getCurrentName() != null) {
                if (parser.getCurrentName().equals("userId")) {
                    int userId = Integer.parseInt(parser.getText());

                    // decide if we move ahead to the next user or not
                    if (previousUserId == -1) {
                        previousUserId = userId;                        
                        user = new User(userId);
                    }  else if (userId != previousUserId) {
                        writeUser(user, fw, actionsByUserId, false);
                        previousUserId = userId;
                        user = new User(userId);
                    } 
                } else if (parser.getCurrentName().equals("playlistId")) {
                    playlist = new Playlist(Integer.parseInt(parser.getText()));
                    user.addPlaylist(playlist);
                } else if (parser.getCurrentName().equals("songId")) {
                    song = new Song(Integer.parseInt(parser.getText()));
                    playlist.addSong(song);
                }
            }
        }
        // write the last user
        writeUser(user, fw, actionsByUserId, true);
        fw.write("\n]");
        in.close();
        fw.close();
                                       
        return outputFileName;
    }

    private void writeUser(User user, FileWriter fw, HashMap<Integer,List<Action>> actionsByUserId, boolean isLast) throws Exception {
        // we started with another user, can serialize the old one
        applyUserChanges(user, actionsByUserId.get(new Integer(user.getUserId())));
        String s = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(user);
        fw.write(s);
        if (!isLast) { fw.write(","); }
        fw.flush();
    }

    private HashMap<Integer,List<Action>> parseChanges(File changesFile) throws Exception {
        HashMap<Integer,List<Action>> actionsByUserId = new HashMap<Integer,List<Action>>();
        String line;
        BufferedReader br = new BufferedReader(new FileReader(changesFile));
        while ((line = br.readLine()) != null) {
            // what type of action are we dealing with ?
            Action action = parseAction(line);
            Integer key = new Integer(action.userId);
            List<Action> actions = actionsByUserId.get(key);
            if (actions == null) {
                actions = new ArrayList<Action>();
                actionsByUserId.put(key, actions);
            }
            actions.add(action);
        }
        br.close();
        return actionsByUserId;
    }

    private void applyUserChanges(User user, List<Action> actions) throws Exception {
        for (Action action: actions) {
            
            if(action.actionType ==  MixtapeAction.REMOVE_PLAYLIST) {
                user.removePlaylist(action.playlistId);
            } else if (action.actionType == MixtapeAction.ADD_PLAYLIST) {
                Playlist playlist = new Playlist(action.playlistId);
                for (int songId : action.songIds) {
                    playlist.addSong(new Song(songId));
                }
                user.addPlaylist(playlist);
            } else if (action.actionType ==  MixtapeAction.ADD_SONG) {
                // find the playlist with this id
                Playlist playlist = user.playlists.stream()
                    .filter(p -> p.playlistId == action.playlistId)
                    .findAny()
                    .orElse(null);
                if (playlist == null) {
                    // blow up the whole request
                    throw new Exception("no playlist with id " + action.playlistId + " found");
                } else {
                    // not a requirement but since it's easily done, we can add multiple songs
                    for (int songId : action.songIds) {                        
                        playlist.addSong(new Song(songId));
                    }
                }
            } else {
                throw new Exception("Unexpected action: " +  action);
            }
        }
    }

    // figure out the type of action and its attributes
    // throws exception if the format of an action is unexpected
    // actions are executed sequentially, in the order present in the file
    private Action parseAction(String s) throws Exception {
        String actionString;
        
        if (s.contains(MixtapeAction.ADD_SONG.name())) {
            actionString = actionFormats.get(MixtapeAction.ADD_SONG).format(s);            
            return getActionWith3Args(MixtapeAction.ADD_SONG, actionString);
        } else if (s.contains(MixtapeAction.ADD_PLAYLIST.name())) {
            actionString = actionFormats.get(MixtapeAction.ADD_PLAYLIST).format(s);
            return getActionWith3Args(MixtapeAction.ADD_PLAYLIST, actionString);
        } else if (s.contains(MixtapeAction.REMOVE_PLAYLIST.name())) {
            actionString = actionFormats.get(MixtapeAction.REMOVE_PLAYLIST).format(s);
            return getActionWith2Args(MixtapeAction.REMOVE_PLAYLIST, actionString);
        } else {
            throw new Exception("Unknown action " + s);
        }
    }

    // TODO: remove code duplication
    private Action getActionWith3Args(MixtapeAction actionType, String actionString) {
        String[] parts = actionString.split("/");
        int userId = Integer.parseInt(parts[0].split("=")[1]);
        int playlistId = Integer.parseInt(parts[1].split("=")[1]);
        // there can be one or more songs, comma-separated
        String songs = parts[2].split("=")[1];
        int[] songIds = Arrays.stream(songs.split(",")).mapToInt(Integer::parseInt).toArray();
        return new Action(actionType, userId, playlistId, songIds);        
    }
    
    private Action getActionWith2Args(MixtapeAction actionType, String actionString) {
        String[] parts = actionString.split("/");
        int userId = Integer.parseInt(parts[0].split("=")[1]);
        int playlistId = Integer.parseInt(parts[1].split("=")[1]);
        return new Action(actionType, userId, playlistId);        
    }

    // old filename + timestamp
    private String deriveOutputFilename(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        return name.substring(0, lastIndexOf) + "_" + System.currentTimeMillis() + name.substring(lastIndexOf);
    }


    // TEST
    static void test() throws Exception {
        Mixtape mx = new Mixtape(new File("test/resources/test.json"));
        String outputFileName =  mx.update(new File("test/resources/testChange.txt"));

        // read the output file as plain json
        File file = new File(outputFileName);
        User[] users = new ObjectMapper().readValue(file, User[].class);
        if (users.length != 2) { throw new Exception("expecting two users"); }
        User user1 = users[0];
        if (user1.getUserId() != 1) { throw new Exception("first user should be user with id 1"); }
        // should have 3 playlists
        if (user1.getPlaylists().size() != 3) { throw new Exception("first user should have 3 playlists"); }
        // second playlist should have 3 songs
        if (user1.getPlaylists().get(1).getSongs().size() != 3) { throw new Exception("first user, second playlist should have 3 songs"); }
        User user2 = users[1];
        // should have 2 playlists
        if (user2.getPlaylists().size() != 2) { throw new Exception("first user should have 2 playlists"); }
        // second playlist should have 3 songs
        if (user2.getPlaylists().get(1).getSongs().size() != 3) { throw new Exception("second user, second playlist should have 3 songs"); }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage is: Mixtape <data file> <changes file>");
            System.exit(0);
        }
        
        File dataFile = new File(args[0]);
        if (!dataFile.exists()) {
            System.out.println("data file " + args[0] + " does not exist");
            System.exit(0);
        }
        
        File changesFile = new File(args[1]);
        if (!changesFile.exists()) {
            System.out.println("changes file " + args[1] + " does not exist");
            System.exit(0);
        }

        try {
            Mixtape mx = new Mixtape(dataFile);
            String outputFileName =  mx.update(changesFile);
            System.out.println("OUTPUT IN: " + outputFileName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // small test setup
        /*
        try {
            test();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        */
    }
}


enum MixtapeAction {
    ADD_SONG,
    ADD_PLAYLIST,
    REMOVE_PLAYLIST;
}

// each action has a user id; and optionally a playlist and song id; a playlistId value of 0 indicates a null playlist
class Action {
    int userId;
    int playlistId = 0;
    int[] songIds = null;
    MixtapeAction actionType;

    Action(MixtapeAction actionType, int userId, int playlistId, int[] songIds) {
        this.actionType = actionType;
        this.userId = userId;
        this.playlistId = playlistId;
        this.songIds = songIds;
    }

    Action(MixtapeAction actionType, int userId, int playlistId) {
        this.actionType = actionType;
        this.userId = userId;
        this.playlistId = playlistId;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("userId:" + userId);
        sb.append("/");
        sb.append("playlistId:" + playlistId);
        sb.append("/");
        sb.append("songIds:" + Arrays.toString(songIds));
        sb.append("/");
        sb.append("action:" + actionType.name());
        return sb.toString();
    }
}


            
