import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

import javafx.embed.swing.JFXPanel;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import org.json.JSONException;
import org.json.JSONObject;

public class MindMusic {
	private static MediaPlayer mediaPlayer;
	private int[][] songOccurrenceArray;
	private int[] lastOccurrenceOfSongs;
	private String[] songs;
	private static int MAX = 60*5; // buffer size of 5 seconds
	public MindMusic() {
		for (int i = 0; i < 3; i++) {
			songOccurrenceArray[i] = new int[MAX];
			lastOccurrenceOfSongs[i] = 0;
			songs = new String[]{"MitiS-Touch.mp3", "TwoThirds-Universal.mp3", "TOTO-Hold The Line.mp3"};
		}
	}
	public static void main(String[] args) throws InterruptedException {	
		//run API_Main for the EPOC server socket
		Thread t = new Thread(new API_Main());
		t.start();
		MindMusic self = new MindMusic();
		JFXPanel panel = new JFXPanel(); // just to initialize javafx
		double threshold = 0.3; // 30% confidence in cognitive action
		String params;
		String JSONResponse;
		BufferedReader inFromUser = new BufferedReader( new InputStreamReader(System.in));
		try {
			//connect to the EPOC server socket
			Socket clientSocket = new Socket("localhost", 4444);
			DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());			
			params = "Pull, Push, Lift";
			BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			outToServer.writeBytes("Frustration, Meditation, Blink, RaiseBrow, Cogntiv" + '\n'); 
			String[] tokens = params.split(", ");
			while ((JSONResponse = inFromServer.readLine()) == null) {
				System.out.println("waiting...");
			}
			while ((JSONResponse = inFromServer.readLine()) != null) {
				JSONObject obj = new JSONObject(JSONResponse);
				System.out.println(obj); //debug
				for (String token : tokens) {
					//for cognitiv events, which are contained in a JSONObject
					if (API_Main.getCogntivMap().containsKey(token)) {
						String cog_action = obj.getJSONObject("EmoStateData").getString("Cognitiv");
						if (cog_action.equals(token)) {
							double param_val = obj.getJSONObject("EmoStateData").getDouble("Cognitiv");
							if (param_val > threshold && token == tokens[0]) {
								self.songOccurred(self.lastOccurrenceOfSongs[0], self.songOccurrenceArray[0]);
							}
							else if (param_val > threshold && token == tokens[1]) {
								self.songOccurred(self.lastOccurrenceOfSongs[1], self.songOccurrenceArray[1]);
							}
							else if (param_val > threshold && token == tokens[2]) {
								self.songOccurred(self.lastOccurrenceOfSongs[2], self.songOccurrenceArray[2]);
							}
							int currentTime = (int) (System.currentTimeMillis() / 1000) % 86400;
							int max = 0;
							int maxSong = -1;
							if (currentTime % 2000 == 0) { //every 2 seconds check if song should be switched
								for (int i = 0; i < 3; i++) {
									int currentCount = self.countOccurrences(self.lastOccurrenceOfSongs[i], self.songOccurrenceArray[i]);
									max = (currentCount > max) ? currentCount : max;
									maxSong = (currentCount > max) ? i : maxSong;
								}
								if (maxSong != -1) self.playMusic(self.songs[maxSong]);
							}
						}
					}
				}
			}
			//close all resources
			clientSocket.close();
			inFromUser.close();
			inFromServer.close();
			outToServer.close();
		}
		catch (SocketException e) {
			System.out.println("Could not start EPOC data server socket, aborting.");
			e.printStackTrace();
			System.exit(-1);
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		/* non-mind-controlled playlist for debugging */
		/*self.playMusic("MitiS-Touch.mp3");
		Thread.sleep(1000);
		System.out.println("Duration: "+mediaPlayer.getMedia().getDuration().toSeconds());
		Thread.sleep((long) mediaPlayer.getMedia().getDuration().toMillis() - 1000);
		self.playMusic("TwoThirds-Universal.mp3");
		Thread.sleep(1000);
		System.out.println("Duration: "+mediaPlayer.getMedia().getDuration().toSeconds());
		Thread.sleep((long) mediaPlayer.getMedia().getDuration().toMillis() - 1000);
		self.playMusic("TOTO-Hold_The_Line.mp3");*/
	}

	public void playMusic(String song) {
		if (mediaPlayer != null) mediaPlayer.stop(); // in case something is already playing
		Media media = new Media("file://" + (new File(song)).getAbsolutePath());
		mediaPlayer = new MediaPlayer(media);	
		mediaPlayer.setOnReady(new Runnable() {
			@Override
			public void run() {
				mediaPlayer.play();
			}
		});
	}

	public int countOccurrences(int lastOccurrence, int[] occurrenceArray) {
		int currentTime = (int) (System.currentTimeMillis() / 1000) % MAX;
		clearStaleData(currentTime, lastOccurrence, occurrenceArray);
		int lastSecond = sumOccurrencesInPastSeconds(currentTime, 1, occurrenceArray);
		// int lastFiveSeconds = sumOccurrencesInPastSeconds(currentTime, MAX, occurrenceArray);		
		return lastSecond;
	}

	public void songOccurred(int lastOccurrence, int[] occurrenceArray) {
		int currentTime = (int) (System.currentTimeMillis() / 1000) % MAX;
		clearStaleData(currentTime, lastOccurrence, occurrenceArray);
		occurrenceArray[currentTime]++;
		lastOccurrence = currentTime;
	}

	public int sumOccurrencesInPastSeconds(int currentTime, int seconds, int[] occurrenceArray) {
		int res = 0;
		for (int i = 0; i < seconds; i++) {
			res += occurrenceArray[((currentTime - i + MAX) % MAX)];
		}
		return res;
	}

	public void clearStaleData(int currentTime, int lastOccurrence, int[] occurrenceArray) {
		// case 1: currentTime is after lastOccurrence time
		// case 2: currentTime has wrapped around to before lastOccurrence time
		if (currentTime > lastOccurrence) {  // case 1
			for (int i = currentTime; i > lastOccurrence; i--) {
				occurrenceArray[i] = 0;
			}
		}
		if (currentTime < lastOccurrence) {  // case 2
			for (int i = 0; i < MAX - (lastOccurrence - currentTime); i++) {
				occurrenceArray[((currentTime - i + MAX) % MAX)] = 0;
			}
		}
	}
}