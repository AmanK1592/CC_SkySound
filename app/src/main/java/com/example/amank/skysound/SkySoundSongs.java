package com.example.amank.skysound;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SkySoundSongs extends AppCompatActivity {

    private FloatingActionButton playPauseButton;
    PlayerService mBoundService;
    boolean mServiceBound = false;
    List<Song> songs = new ArrayList<>();
    ListView songsListView;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            PlayerService.MyBinder myBinder = (PlayerService.MyBinder) service;
            mBoundService = myBinder.getService();
            mServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mServiceBound = false;
        }
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isPlaying = intent.getBooleanExtra("isPlaying",false);
            flipPlayPauseButton(isPlaying);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sky_sound_songs1);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        playPauseButton = (FloatingActionButton) findViewById(R.id.playpause1);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mServiceBound){
                    mBoundService.togglePlayer();
                }
            }
        });

        String url = "http://79.170.40.180/cloudatlas.com/Music_files/bensound-cute.mp3";
        songsListView = (ListView) findViewById(R.id.SongsListView);
        fetchSongsFromWeb();
    }

    private void startStreamingService(String url){

        Intent i = new Intent(this,PlayerService.class);
        i.putExtra("url",url);
        i.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
        startService(i);
        bindService(i,mServiceConnection,Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mServiceBound){
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,new IntentFilter("changePlayButton"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    public void flipPlayPauseButton (boolean isPlaying){
        if(isPlaying){
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        }else{
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void fetchSongsFromWeb(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                InputStream inputStream = null;

                try{
                    URL url = new URL("http://79.170.40.180/cloudatlas.com/Music_files/getmusic.php");
                    urlConnection = (HttpURLConnection)url.openConnection();
                    urlConnection.setRequestMethod("GET");

                    int statuscode= urlConnection.getResponseCode();
                    if(statuscode == 200){
                        inputStream = new BufferedInputStream((urlConnection.getInputStream()));
                        String response = convertInputStreamToString(inputStream);
                        Log.i("Got the songs",response);
                        parseIntoSongs (response);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    if(urlConnection != null){
                        urlConnection.disconnect();
                    }
                }
            }
        });
        thread.start();
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String line = "";
        String result = "";

        while((line = bufferedReader.readLine()) != null){
            result += line;
        }
        if(inputStream != null){
            inputStream.close();
        }
        return result;
    }

    private void parseIntoSongs (String data){
        String[] dataArray = data.split("\\*");
        int i=0;

        for(i=0; i<dataArray.length;i++){
            String[] songArray = dataArray[i].split(",");
            Song song = new Song(songArray[0],songArray[1],songArray[2],songArray[3]);
            songs.add(song);
        }

        for (i=0;i<songs.size();i++){
            Log.i("GOT SONG",songs.get(i).getTitle());
        }

        populateSongsListView();
    }

    private void populateSongsListView(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SongListAdapter adapter = new SongListAdapter(SkySoundSongs.this,songs);
                songsListView.setAdapter(adapter);
                songsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        Song song = songs.get(position);
                        String songAddress = "http://79.170.40.180/cloudatlas.com/Music_files/"+song.getTitle();
                        startStreamingService(songAddress);
                        markSongPlayed(song.getId());
                        askForLikes(song);

                    }
                });
            }
        });
    }

    private void markSongPlayed(final int chosenId){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                HttpURLConnection urlConnection = null;

                try{
                    URL url = new URL("http://79.170.40.180/cloudatlas.com/Music_files/addplay.php?id="+ Integer.toString(chosenId));
                    urlConnection = (HttpURLConnection)url.openConnection();
                    urlConnection.setRequestMethod("GET");

                    int statuscode = urlConnection.getResponseCode();
                    if(statuscode == 200){
                        inputStream = new BufferedInputStream(urlConnection.getInputStream());
                        String response = convertInputStreamToString(inputStream);
                        Log.i("Played Song ID",response);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
                finally{
                    if(urlConnection !=null)
                        urlConnection.disconnect();
                }
            }
        });
        thread.start();

    }

    private void askForLikes(final Song song){
        new AlertDialog.Builder(this)
                .setTitle(song.getTitle())
                .setMessage("Do you like this song?")
                .setPositiveButton("YES!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        likeSong(song.getId());
                    }
                })
                .setNegativeButton("NO :(", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();

    }

    private void likeSong(final int chosenId){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                HttpURLConnection urlConnection = null;

                try{
                    URL url = new URL("http://79.170.40.180/cloudatlas.com/Music_files/addlike.php?id="+ Integer.toString(chosenId));
                    urlConnection = (HttpURLConnection)url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.getResponseCode();
                }catch(Exception e){
                    e.printStackTrace();
                }
                finally{
                    if(urlConnection !=null)
                        urlConnection.disconnect();
                }
            }
        });
        thread.start();

    }


}