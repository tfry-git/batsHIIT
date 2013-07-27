package org.jfet.batsHIIT;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HIITRun extends Activity {
    // sound manager
    private SoundManager sndMan;
    // resource reference and SoundManager IDs for the sounds we'll use
    private final static int beep1Snd = R.raw.beep1;
    private final static int beep2Snd = R.raw.beep2;
    private final static int chirpSnd = R.raw.chirp;
    private int beep1;
    private int beep2;
    private int chirp;
    private LinearLayout lLayout;
    private TextView nSeconds;
    private TextView nIntervals;
    private TextView nBlocks;
    private Handler uiHandler;
    private HIITRunner hiitRunner;
    private int workSeconds;
    private int breakSeconds;
    private int restSeconds;
    private int intervalCount;
    private int blockCount;
    private WakeLock scrUnLock;
    
    private static enum HIITState { WORK, BREAK, REST };
    
    private class HIITRunner extends Thread {
    //Thread hiitRunner = new Thread() {
    	private volatile boolean runLoop;
    	
    	public void stopRunner() {
    		this.runLoop = false;
    	}
    	
    	@Override
    	public void run() {
    		// run the loop
       		this.runLoop = true;
       		// initialize state machine
    		int timeRemaining = HIITRun.this.restSeconds;
    		int blockRemaining = HIITRun.this.blockCount;
    		int intvRemaining;
    		HIITState hiitState = HIITRun.HIITState.REST;
    		// variables for delay-locked loop
    		final long sleepTarget = 1000000000L;
    		long sleepDelay = sleepTarget;
    		long wakeupError;
    		long lastWakeup;
    		long thisWakeup;

           	HIITRun.this.sndMan.pauseUntilLoaded(HIITRun.this.beep1 +
           										 HIITRun.this.beep2 +
        										 HIITRun.this.chirp);    		
           	
           	// start out with 5 warning beeps, then the workout begins
           	for (intvRemaining = 5; intvRemaining > 0; intvRemaining--)
           		try {
           			HIITRun.this.sndMan.playSound(HIITRun.this.beep1);
           			Thread.sleep(1000);
           		} catch (InterruptedException ex) { continue; }

           	lastWakeup = System.nanoTime() - sleepTarget;
    		while (runLoop) {
    			thisWakeup = System.nanoTime();
    			if (timeRemaining == 1) { // finished this subinterval
    				switch (hiitState) {
    				case WORK:
    				// WORK -> BREAK : update state, update time; no change to blocks or intvs
    					hiitState = HIITRun.HIITState.BREAK;
    					timeRemaining = HIITRun.this.breakSeconds;
    					HIITRun.this.sndMan.playSound(HIITRun.this.beep2);
    					HIITRun.this.uiHandler.obtainMessage(1).sendToTarget();
    					break;
    				
    				case BREAK:
    				// BREAK transition, either to WORK or to REST
    					if (intvRemaining == 0) {	// to REST
    						hiitState = HIITRun.HIITState.REST;
    						timeRemaining = HIITRun.this.restSeconds;
    						HIITRun.this.uiHandler.obtainMessage(2,0,blockRemaining).sendToTarget();
    					} else {					// back to WORK
    						hiitState = HIITRun.HIITState.WORK;
    						timeRemaining = HIITRun.this.workSeconds;
    						intvRemaining--;
    						HIITRun.this.sndMan.playSound(HIITRun.this.beep1);
    						HIITRun.this.uiHandler.obtainMessage(0,intvRemaining,blockRemaining).sendToTarget();
    					}
    					break;
    				
    				case REST:
    				// REST transition, either to WORK or done
    					if (blockRemaining == 0) {	// all done!
    						HIITRun.this.uiHandler.obtainMessage(4).sendToTarget();
    					} else {					// back to WORK
    						hiitState = HIITRun.HIITState.WORK;
    						timeRemaining = HIITRun.this.workSeconds;
    						intvRemaining = HIITRun.this.intervalCount - 1;
    						blockRemaining--;
    						HIITRun.this.sndMan.playSound(HIITRun.this.beep1);
    						HIITRun.this.uiHandler.obtainMessage(0,intvRemaining,blockRemaining).sendToTarget();
    					}
    				}
    			} else { // no transition; just update the timer display and possibly play the warning chirps
    				timeRemaining--;
    				HIITRun.this.uiHandler.obtainMessage(3,timeRemaining,0).sendToTarget();
    				if (timeRemaining < 5) HIITRun.this.sndMan.playSound(HIITRun.this.chirp);
    			}
    			
    			// delay-locked loop
    			// reference delay is 1 second (sleepTarget, in nanoseconds)
    			// measure delay each cycle by looking at new value from System.nanoTime()
    			// integrator:
    			//   y[n] = y[n-1] + x[n]/30
    			//   Y = Y*z^-1 + X/30
    			//   Y/X = 1/30/(1 - z^-1)
    			// feedforward (zero)
    			//   z[n] = x[n]/5
    			//   Z/X = 1/5
    			// total transfer function
    			//   w[n] = y[n] + z[n]
    			//   W/X = Y/X + Z/X
    			//       = 1/30/(1 - z^-1) + 1/5
    			//		 = (6*(1 - z^-1) + 1)/30/(1 - z^-1)
    			wakeupError = sleepTarget - thisWakeup + lastWakeup;		// error signal
    			sleepDelay = sleepDelay + wakeupError / 30;					// integrator strength 1/30
    			wakeupError = sleepDelay + (wakeupError / 5);				// feedforward strength 1/5
    			lastWakeup = thisWakeup;									// save most recent wakeup
    			
    			//Log.w("org.jfet.batsHIIT",String.format("dly %d",wakeupError));

            	try { Thread.sleep(wakeupError / 1000000L); }	// sleep 1 second
            	catch (InterruptedException ex) { continue; }
            }
    		
    		HIITRun.this.uiHandler.obtainMessage(4).sendToTarget();
    	}
    };
    
    // create the activity. In addition to standard activities
    // we create a soundpool and tell it to preload the sounds
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// setup the action bar with a back button, if supported
        setupActionBar();
        
        // create the sound manager instance
		this.sndMan = new SoundManager(this);
        // load the sounds to initialize the sound manager
        this.beep1 = sndMan.loadSound(beep1Snd);
        this.beep2 = sndMan.loadSound(beep2Snd);
        this.chirp = sndMan.loadSound(chirpSnd);
        
        // set up the workout parameters
        Intent itt = this.getIntent();
        this.workSeconds = itt.getIntExtra(HIITMain.M_WORK, 50);
        this.breakSeconds = itt.getIntExtra(HIITMain.M_BREAK, 10);
        this.restSeconds = itt.getIntExtra(HIITMain.M_REST, 60);
        this.intervalCount = itt.getIntExtra(HIITMain.M_INTV, 7);
        this.blockCount = itt.getIntExtra(HIITMain.M_BLOCK, 3);

        // make sure the screen stays on through the workout
        // this works, but always keeps the screen at 100% brightness
        //this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager pm = (PowerManager) this.getSystemService(POWER_SERVICE);
        this.scrUnLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK |
        								PowerManager.ON_AFTER_RELEASE     ,
        						   		"org.jfet.batsHIIT.HIITRun.scrUnLock");
        scrUnLock.acquire();

        // create a handler for hiitRunner to send us UI updates
        this.uiHandler = new Handler(Looper.getMainLooper()) {
        	// handle the message from the hiitRunner thread
        	@Override
        	public void handleMessage (Message m) {
        		// if the message type is different than last time, update the view to the new one
        		switch (m.what) {
        		
        		case 0:
        			// change UI to WORK
        			HIITRun.this.setContentView(R.layout.activity_hiitrun);
        			HIITRun.this.lLayout = (LinearLayout) findViewById(R.id.hiitRunLayout);
        			HIITRun.this.nSeconds = (TextView) findViewById(R.id.nSeconds);
        			HIITRun.this.nIntervals = (TextView) findViewById(R.id.nIntervals);
        			HIITRun.this.nBlocks = (TextView) findViewById(R.id.nBlocks);
        			// update values
        			HIITRun.this.lLayout.setBackgroundColor(Color.GREEN);
        			HIITRun.this.nSeconds.setText(String.format("%d",HIITRun.this.workSeconds));
        			HIITRun.this.nIntervals.setText(String.format("%d",m.arg1));
        			HIITRun.this.nBlocks.setText(String.format("%d",m.arg2));
        			break;
        		
        		case 1:
        			// change UI to BREAK
        			HIITRun.this.lLayout.setBackgroundColor(Color.YELLOW);
        			HIITRun.this.nSeconds.setText(String.format("%d", HIITRun.this.breakSeconds));
        			break;
        			
        		case 2:
        			// change UI to REST
        			HIITRun.this.setContentView(R.layout.activity_hiitrun_rest);
        			HIITRun.this.lLayout = (LinearLayout) findViewById(R.id.hiitRunLayoutRest);
        			HIITRun.this.nSeconds = (TextView) findViewById(R.id.nSecondsRest);
        			HIITRun.this.nBlocks = (TextView) findViewById(R.id.nBlocksRest);
        			// update values
        			HIITRun.this.lLayout.setBackgroundColor(Color.RED);
        			HIITRun.this.nSeconds.setText(String.format("%d",HIITRun.this.restSeconds));
        			HIITRun.this.nBlocks.setText(String.format("%d",m.arg2));
        			break;
        		
        		case 3:
        			// update seconds only
        			HIITRun.this.nSeconds.setText(String.format("%d",m.arg1));
        			break;

        		case 4:
        			HIITRun.super.onBackPressed();
        			break;
        		}
        	}
        };

        this.uiHandler.obtainMessage(2,0,this.blockCount).sendToTarget();
        hiitRunner = new HIITRunner();
        hiitRunner.start();
	}
	
	// onPause is always called when the activity is undisplayed
	// stop the counter thread here
	@Override
	protected void onPause() {
		super.onPause();
		hiitRunner.stopRunner();
		hiitRunner.interrupt();
		this.scrUnLock.release();
		super.onBackPressed();
	}
	
    // Set up the {@link android.app.ActionBar}, if the API is available.
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    getActionBar().setDisplayHomeAsUpEnabled(true);
            }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
            case android.R.id.home:
                    // This ID represents the Home or Up button.
                    NavUtils.navigateUpFromSameTask(this);
                    return true;
            }
            return super.onOptionsItemSelected(item);
    }

    public void playBeep1 (View view) { sndMan.playSound(this.beep1); }
    public void playBeep2 (View view) { sndMan.playSound(this.beep2); }
    public void playChirp (View view) { sndMan.playSound(this.chirp); }

}
