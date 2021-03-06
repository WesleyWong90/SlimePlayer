package slime.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import slime.controller.MediaController;

import slime.media.LibraryPlayList;
import slime.media.PlayList;
import slime.media.PlayState;
import slime.observe.GuiObserver;
import slime.observe.GuiSubject;
import slime.observe.StateObserver;
import slime.observe.StateSubject;
import slime.song.Song;
import slime.song.SongList;
import slime.song.SongTag;

/**
 * <B>
 * This class managers the entire music playlist or library, derived from music information
 * saved in the holdings file, which is in the data folder of the root directory.
 * </B>
 * <p>
 * MusicLibraryManager has two constructors. The first {@link #MusicLibraryManager(String dir)}
 * takes a directory as argument. This is used as the root music directory. The second {@link #MusicLibraryManager(SongList)}
 * takes as input a {@link PlayList }
 * </p>
 * 
 * @see GuiSubject
 * @see StateSubject
 * @see MediaController
 * 
 * 
 * @author dMacGit
 *
 */
public class MusicLibraryManager implements StateSubject, GuiObserver
{
	private final static String NAME = "[Manager]";
	private GuiSubject parentSubject;
	
	private static LibraryPlayList playerLibrary;
	
	private MediaController mediaController;
	
	private List<StateObserver> stateObserverList = new ArrayList<StateObserver>();    
    
    /*
     * Needed to carry the names of the observers classes in order to keep track of what observer
     * has called-back to the parent subject. 
     * Was using a count variable but this wouldn't account for observers that unintentionally callback
     * twice or more.
     */
    
    //Using an array of observer names as string
    
    //ArrayList is overkill for just two observers names but is quick to implement
    private ArrayList<String> observerNamesList = new ArrayList<String>();				//Maybe do not need this!!??!!
    private HashMap<String,Boolean> observersCalledback = new HashMap<String, Boolean>();
    
    private boolean STOP_MANAGER = false, shuffle_Is_On = false, repeat_Is_On = false, PLAY_STATE_CHANGED = false, SYNC_CHANGED;
    
    /*
     * Observer call-backs will sync up all observers and modify these values
     * at each stage of the Manager State change.
     * 
     * TODO: Needs removing!
     */
    private boolean observerSyncReady = false;
    private boolean observerSyncPlay = false;
    private boolean observerSyncClose = false;
    
    
    //What user button was pressed
    private boolean userPressedPlay = false;
    private boolean userPressedPause = false;
    private boolean userPressedSkip_next = false;
    private boolean userPressedSkip_back = false;
    private boolean userPressedStop = false;
    private boolean userToggledShuffle = false;
    private boolean userToggledRepeat = false;
    private boolean userSkippedForwards = false;
    private boolean userSkippedBack = false;
    private boolean userPressedClose = false;
    
    private boolean reachedEndOfPlaylist = false;
    
    private playlistManagerThread playListThread;
    
    private String Durration;
    private PlayState currentPlayState;

    public MusicLibraryManager(boolean emptyLibrary)
    {    	
    	playerLibrary = new LibraryPlayList();
    	if(!emptyLibrary)
    	{
    		playerLibrary.createLibraryPlaylist();
        	System.out.println(NAME+" The list of songs is this large: "+playerLibrary.getTotalNumberTracks()+" Songs!");
            
            mediaController = new MediaController();

            System.out.println(NAME+" "+MediaController.class.getName()+" created!");
            
            observerNamesList.add(MediaController.class.getName());

            observersCalledback.put(MediaController.class.getName(),false);
            
            playListThread = new playlistManagerThread(playerLibrary);
            playListThread.start();
                 
            this.registerStateObserver(mediaController);
            mediaController.setParentSubject(this);
            
            System.out.println(NAME+" Player has been Started!");
    	}
    	else
    		System.out.println(NAME+" Player is created but has no PlayList and is Not Started!");
    	//Maybe populate library here!
    	
        
    }
    
    public MusicLibraryManager(SongList playList)
    {
        mediaController = new MediaController();        
        
        playerLibrary = new LibraryPlayList();
    	
    	//Maybe populate library here!
    	playerLibrary.createLibraryPlaylist();
    	System.out.println(NAME+" The list of songs is this large: "+playerLibrary.getTotalNumberTracks()+" Songs!");
        
        playListThread = new playlistManagerThread(playerLibrary);
        playListThread.start();
        
        this.registerStateObserver(mediaController);
        mediaController.setParentSubject(this);
        
        System.out.println(NAME+" Player has been Started!");
        
    }
    
    public void startupLibraryManager()
    {
    	playerLibrary.createLibraryPlaylist();
    	System.out.println(NAME+" The list of songs is this large: "+playerLibrary.getTotalNumberTracks()+" Songs!");
        
        mediaController = new MediaController();

        System.out.println(NAME+" "+MediaController.class.getName()+" created!");
        
        observerNamesList.add(MediaController.class.getName());

        observersCalledback.put(MediaController.class.getName(),false);
        
        playListThread = new playlistManagerThread(playerLibrary);
        playListThread.start();
             
        this.registerStateObserver(mediaController);
        mediaController.setParentSubject(this);
        
        System.out.println(NAME+" Player has been Started!");
    }
    
    public boolean forceLibraryPlaylistUpdate()
    {
    	playerLibrary.createLibraryPlaylist();
    	startupLibraryManager();
    	return true;
    }
    public List<SongTag> getMapOfSong() throws Exception
    {
    	if(this.playerLibrary != null && playerLibrary.getTotalNumberTracks() > 0)
    		return playerLibrary.getSongTags();
        return null;
    }
    
    public class playlistManagerThread extends Thread
    {   	
    	public int currentIndex = -1, nextSongIndex = 0;
    	private LibraryPlayList playerLibrary;
    	
        public playlistManagerThread(LibraryPlayList playerLibrary)
        {
        	this.playerLibrary = playerLibrary;
        	currentPlayState = PlayState.INITIALIZED;
        	System.out.println(NAME+" The Playlist Manager Thread is created!");
        }

        @Override
        public void run()
        {
            while(!STOP_MANAGER)
            {
            	
            	if(currentPlayState == PlayState.INITIALIZED)
                { 
        			System.out.println(NAME+" The Manager thread is initialized!");
        			PLAY_STATE_CHANGED = !PLAY_STATE_CHANGED;
        			currentPlayState = PlayState.IDLE;
        			notifyAllStateObservers(null,currentPlayState);

                }
            	
            	if(PLAY_STATE_CHANGED || SYNC_CHANGED && !reachedEndOfPlaylist)
            	{
            		/*	The initial state of the manager and the Animator and MediaController
            		 * 	It is assumed that the Animator and MediaController classes are Initiated
            		 * 	on creation.
            		 */
            		
            		//The Idle state of the Manager. Not worrying about the observer idle states
            		if(currentPlayState == PlayState.IDLE && userPressedPlay)
                    {
            			System.out.println(NAME+" Changing from IDLE to READY");
            			
            			if(shuffle_Is_On)
                    	{
            				System.out.println(NAME+" SHUFFLE Active, Choosing random song");
                    	}
                    	else
                    	{
                    		System.out.println(NAME+" SHUFFLE Inactive, Choosing next song");
                    	}
            			if(playerLibrary.chooseNextTrack())
        				{
        					System.out.println(NAME+" Selected Index: "+playerLibrary.getPlayCount()+" which holds: "+playerLibrary.getCurrentTrack_MetaData().getRecordingTitle());
        					currentPlayState = PlayState.READY;
        					notifyAllStateObservers(playerLibrary.getCurrentTrack(),currentPlayState);
                			PLAY_STATE_CHANGED = false;
                			resetUserButtons();
                    		
        				}
        				else
        				{
        					//Notify end of playlist!
        					//currentPlayState = PlayState.IDLE;
        					//endOfPlaylist = true;
        					reachedEndOfPlaylist = true;
        				}
            			
                    }
            		else if(currentPlayState == PlayState.READY)
                    {
            			System.out.println(NAME+" READY for Observer SYNC-READY");
            			if(observerSyncReady)
                		{
            				System.out.println(NAME+" Observer SYNC-READY Manager Changing to PLAYING");
                			currentPlayState = PlayState.PLAYING;
                			notifyAllStateObservers(null,currentPlayState);
                			observerSyncReady = false;
                			PLAY_STATE_CHANGED = false;
                			SYNC_CHANGED = false;
                		}            			
                    }
            		else if(currentPlayState == PlayState.FINISHED)
                    {
                		//First Check for end of playlist
                		if( playerLibrary.chooseNextTrack() )
                		{
							currentPlayState = PlayState.READY;
							PLAY_STATE_CHANGED = false;
							notifyAllStateObservers(playerLibrary.getCurrentTrack(), currentPlayState);
							System.out.println(NAME+" Current Song / Next Song to play: "
									+ playerLibrary.getCurrentTrack_MetaData().getSongTitle());
							int checkTime = (playerLibrary.getCurrentTrack_MetaData().getDurration()) / 2;
							int realTime = (checkTime % 60);
							System.out.println(NAME+" "+playerLibrary.getCurrentTrack_MetaData().getSongTitle() + " <=[" + Durration
									+ "]=> " + checkTime + " ---> " + (int) (checkTime / 60) + ":" + realTime);
                        }
                        else
                        {
							currentPlayState = PlayState.END_OF_PLAYLIST;
							PLAY_STATE_CHANGED = false;
							parentSubject.guiCallback(PlayState.END_OF_PLAYLIST, null);
							notifyAllStateObservers(playerLibrary.getCurrentTrack(), currentPlayState);
                        }                        	
						
                    }
            		else if(currentPlayState == PlayState.PLAYING && observerSyncPlay)
                    {
            			observerSyncPlay = false;
						PLAY_STATE_CHANGED = !PLAY_STATE_CHANGED;
                    }
            		else if(currentPlayState == PlayState.PLAYING && userPressedPause)
                    {
						currentPlayState = PlayState.PAUSED;
						notifyAllStateObservers(null, currentPlayState);
						PLAY_STATE_CHANGED = !PLAY_STATE_CHANGED;
						resetUserButtons();
                    }
            		else if(currentPlayState == PlayState.PAUSED && userPressedPlay)
                    {
						currentPlayState = PlayState.PLAYING;
						notifyAllStateObservers(null, currentPlayState);
						PLAY_STATE_CHANGED = !PLAY_STATE_CHANGED;
						resetUserButtons();
                    }
            		else if( (currentPlayState == PlayState.PAUSED || currentPlayState == PlayState.PLAYING) && userSkippedForwards)
                    {
            			currentPlayState = PlayState.READY;
            			PLAY_STATE_CHANGED = !PLAY_STATE_CHANGED;
            			notifyAllStateObservers(null, PlayState.SKIPPED_FORWARDS);
						resetUserButtons();
                    }

            		else if(userPressedClose)
            		{
            			currentPlayState = PlayState.SHUTDOWN;
            			SYNC_CHANGED = false;
            			System.out.println(NAME+" Is Now in "+currentPlayState.toString());
            			PLAY_STATE_CHANGED = false;
            			notifyAllStateObservers(null, currentPlayState);
            			resetUserButtons();
            		}
            		else if(currentPlayState == PlayState.SHUTDOWN)
            		{
            			if(observerSyncClose)
            			{
            				//Fully shut down the program!
            				System.out.println(NAME+" Observers Have Stopped! Deregistering...");
            				deregisterStateObserver(mediaController);
            				System.out.println(NAME+" Observers Deregistered");
            				mediaController = null;
            				System.out.println(NAME+" Closing Manager Thread!");
            				
            				parentSubject.guiCallback(PlayState.SHUTDOWN, null);
            				STOP_MANAGER = true;
            				System.out.println(NAME+" GUI Callback... ");
            			}
            		}
            	}
            	else
            	{
            		try
                    {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException ex)
                    {
                        System.out.println(NAME+" InterruptedException sleeping song player! "+ex);;
                    }
            	}
            }
        }
    }
    
    private void resetUserButtons()
    {
    	userPressedPlay = false;
        userPressedPause = false;
        userPressedSkip_next = false;
        userPressedSkip_back = false;
        userPressedStop = false;
        userToggledShuffle = false;
        userToggledRepeat = false;
        userSkippedForwards = false;
        userSkippedBack = false;
        userPressedClose = false;
    }
    
	public SongTag getTheCurrentSongTag() throws NullPointerException
	{
		if(playerLibrary==null)
		{
			throw new NullPointerException();
		}
		return playerLibrary.getCurrentTrack_MetaData();
	}

	@Override
	public void stateSubjectCallback(String observerName, PlayState state, Song song) 
	{
		
		//Called by the MediaController when the song file has reached the end of play.
		if(state == PlayState.FINISHED)
		{
			
			currentPlayState = PlayState.FINISHED;
			PLAY_STATE_CHANGED = true;
		}
		else if(state == PlayState.READY)
		{
			
			if(playerLibrary.getCurrentTrack() != null && playerLibrary.getCurrentTrack().getMetaTag()!=null)
			{
				System.out.println(NAME+" Recieved READY Callback!");
				parentSubject.guiCallback(state, playerLibrary.getCurrentTrack());
			}
			PLAY_STATE_CHANGED = true;
			SYNC_CHANGED = true;
			observerSyncReady = true;
			//checkObserverSync(observerName,PlayState.READY);
		}
		else if(state == PlayState.SHUTDOWN)
		{
			System.out.println(NAME+" MediaController sent SHUTDOWN Callback!");
			SYNC_CHANGED = true;
			this.observerSyncClose = true;
			System.out.println(NAME+" Commencing SHUTDOWN!");
			
			//checkObserverSync(observerName,PlayState.SHUTDOWN);
		}
	}

	@Override
	public void registerStateObserver(StateObserver observer) 
	{
		stateObserverList.add(observer);
		System.out.println(NAME+" <<<< "+observer.getStateObserverName()+" Added! >>>>");
		
	}

	@Override
	public void deregisterStateObserver(StateObserver observer) 
	{
		stateObserverList.remove(observer);
		System.out.println(NAME+" <<<< "+observer.getStateObserverName()+" Removed! >>>>");
		
	}

	@Override
	public void notifyAllStateObservers(Song currentSong, PlayState state) 
	{
		for(StateObserver observer : stateObserverList)
		{
			observer.updateStateObserver(currentSong, state);
		}
		
	}

	@Override
	public String getGuiObserverName() 
	{
		return this.getClass().getName();
	}

	@Override
	public void setParentSubject(GuiSubject subject) 
	{
		this.parentSubject = subject;
	}

	@Override
	public void updateGuiObserver(PlayState newState) 
	{		
		System.out.println(NAME+" "+this.getGuiObserverName()+" [ Gui State Changed! "+newState.toString()+" ]");
		PLAY_STATE_CHANGED = true;
		if(newState == PlayState.PLAYING)
		{
			userPressedPlay = true;
		}
		else if(newState == PlayState.PAUSED)
		{
			userPressedPause = true;
		}
		/*else if(newState == PlayState.STOPPED)
		{
			userPressedStop = true;
		}*/
		else if(newState == PlayState.SHUTDOWN)
		{
			userPressedClose = true;
			if(playListThread == null)
			{
				currentPlayState = newState;
				parentSubject.guiCallback(currentPlayState, null);
			}
		}
		else if(newState == PlayState.SHUFFLE_TOGGLED)
		{
			userToggledShuffle = true;
			if(shuffle_Is_On)
			{
				shuffle_Is_On = false;
			}
			else
				shuffle_Is_On = true;
			
			if(playerLibrary != null)
			{
				playerLibrary.toggleShuffle(shuffle_Is_On);	
			}
			resetUserButtons();
		}
		else if(newState == PlayState.REPEAT_TOGGLED)
		{
			userToggledRepeat = true;
			repeat_Is_On = !repeat_Is_On;
			if(playerLibrary != null)
			{
				playerLibrary.repeatToggled(repeat_Is_On);	
			}
			resetUserButtons();
		}
		else if(newState == PlayState.SKIPPED_FORWARDS)
		{
			userSkippedForwards = true;
		}
		else if(newState == PlayState.SKIPPED_BACK)
		{
			userSkippedBack = true;
		}
	}
}
