package org.ligi.gobandroid.logic;


import java.util.Vector;

import android.util.Log;

/**
 * 
 * Class to represent a Go Game with its rules
 *
 * @author <a href="http://ligi.de">Marcus -LiGi- Bueschleb </a>
 * 
 * This software is licenced with GPLv3         
 */

public class GoGame implements GoDefinitions {

	private byte act_player=PLAYER_BLACK;
    
    private GoBoard visual_board; // the board to show to the user
    private GoBoard calc_board;	  // the board calculations are done in
    private GoBoard last_board;   // board to detect KO situations
    private GoBoard pre_last_board;   // board to detect KO situations

    
    //private boolean black_to_move = true;

    private boolean last_action_was_pass=false;
   
    private boolean game_finished=false;
    
    private int[][] groups; // array to build groups
        
    public int[][] area_groups; // array to build groups
    public byte[][] area_assign; // cache to which player a area belongs in a finished game
    
    
    //private boolean[][] dead_stones; // dead stone marker
        
    private int group_count = -1;
        
    private int captures_white; // counter for the captures from black
    private int captures_black; // counter for the captures from white
    
    
    public int territory_white; // counter for the captures from black
    public int territory_black; // counter for the captures from white
    
    private int handicap=0;
    
    float komi=6.5f;
    
    public float getKomi() {
    	return komi;
    }

    public float getPointsWhite() {
    	return komi+captures_white+territory_white;
    }
    
    public float getPointsBlack() {
    	return captures_black+territory_black;
    }
    public Vector<byte[]> moves;

    public GoGame( byte size ) {
    	construct(size);
    }
    
    public GoGame(byte size,byte handicap) {
    	this.handicap=handicap;
    	construct(size);
    }
    
    private void apply_handicap() {
    	for (int i=0;i<handicap;i++)
            if (getBoardSize()==19)
            	calc_board.setCellBlack(hoshis19x19[i][0], hoshis19x19[i][1]);
            else if (getBoardSize()==13)
            	calc_board.setCellBlack(hoshis13x13[i][0], hoshis13x13[i][1]);
            else if (getBoardSize()==9)
            	calc_board.setCellBlack(hoshis9x9[i][0], hoshis9x9[i][1]);
            
    }

    private void construct(byte size) {
    	// create the boards
        calc_board = new GoBoard( size );
        
        apply_handicap();
        
        visual_board=calc_board.clone();
        last_board=calc_board.clone();
        pre_last_board=calc_board.clone();
        
        // create the array for group calculations
        groups = new int[size][size];
        
        area_groups = new int[size][size];
        area_assign = new byte[size][size];
                
        //dead_stones=new boolean[size][size];
        
        /*for (int x=0;x<size;x++)
        	for (int y=0;y<size;y++)
        		dead_stones[x][y]=false;
        	*/
        reset();	
        
        
    }
    public void reset() {
    	// black always starts
    	act_player=PLAYER_BLACK;
    	
    	// create the vector to save the moves
        moves= new Vector<byte[]>();
        captures_black=0;
    	captures_white=0;
    }
    
    public void pass() {
        if (last_action_was_pass) {   	// finish game if both passed  
            game_finished=true; 
        	buildGroups();	
        	buildAreaGroups();	
        }
        else {
            last_action_was_pass=true;
            moves.add(new byte[] { -1,-1} );
            setNextPlayer();
        }
    }
/*
    public boolean isStoneDead(byte x,byte y) {
    	return dead_stones[x][y];
    }
*/  
    /**
     *  place a stone on the board
     *
     * @param x
     * @param y
     * @return true if the move was valid - false if invalid move
     */
    public boolean do_move( byte x, byte y ) {
        if ((x >= 0) && (x <= calc_board.getSize()) && (y >= 0) && (y < calc_board.getSize())) { // if x and y are inside the board
        	
        	if(game_finished)
        	{ // game is finished - players are marking dead stones
        		for (int xg = 0; xg < calc_board.getSize(); xg++)
                    for (int yg = 0; yg < calc_board.getSize(); yg++)
                        if (groups[xg][yg]==groups[x][y])
                        	calc_board.toggleCellDead(xg, yg);
        		buildAreaGroups();
        		
        	}
        	else {
        	
            if (calc_board.isCellFree( x, y )) { // cant place a stone where another is allready
                
            	GoBoard bak_board=calc_board.clone();
            	
                if (isBlackToMove())
                    calc_board.setCellBlack( x, y );
                else
                    calc_board.setCellWhite( x, y );
                
                buildGroups();
                remove_dead(x,y);
                
                if ((hasGroupLibertie(groups[x][y])||isDeadGroupOnBoard(x,y)) // if either a field has libertys or get's one
                		&&!pre_last_board.equals(calc_board)) // and the move is not a ko 
                { 	// valid move -> do things needed to do after a valid move 
                    Log.d("gobandroid", "isDeadGroupOnBoard(x,y)" + isDeadGroupOnBoard(x,y));
                    setNextPlayer();
                    
                    
                    pre_last_board=last_board.clone();
                    last_board=calc_board.clone();
                    visual_board=calc_board.clone();                    
                    last_action_was_pass=false;
                    moves.add(new byte[] { x,y} );
                    
                    return true;
                    }
                else { // was an illegal move -> undo
                    calc_board=bak_board.clone();
                    return false;
                }
                
            }
            }
        }
        return false;
    }

    /** 
     * moving without checks 
     * usefull  e.g. for undo / recorded games 
     * where we can be sure that the move is valid 
     * 
     **/
    public void do_internal_move( byte x, byte y ) {
        if (isBlackToMove())
            calc_board.setCellBlack( x, y );
        else
            calc_board.setCellWhite( x, y );
                
        setNextPlayer();
        
        buildGroups();
        remove_dead(x,y); 
        moves.add(new byte[] { x,y} );
    }

    public boolean canUndo() {
        return (moves.size()>0); 
    }
    
    
    /**
     * 
     * undo the last move
     * 
     */
    
    public void undo() {
    	last_action_was_pass=false;
        clear_calc_board();
        
       	Vector<byte[]> _moves=  (Vector<byte[]>)moves.clone();
       	reset();
        
        for (int step=0 ; step<_moves.size()-1;step++)
        {
            byte move_x=_moves.get(step)[0];
            byte move_y=_moves.get(step)[1];
            if (move_x==-1) // move was a pass
            	setNextPlayer();
            else
            	do_internal_move(move_x,move_y);
        }
        
        visual_board=calc_board.clone();
    }
   
    
    public boolean cell_has_liberty(int x , int y )    {
      
      return ( ((x != 0)&&(calc_board.isCellFree( x- 1, y ) ))
          ||
       ((y != 0)&&(calc_board.isCellFree( x, y - 1 )))
          ||  
       ((x != (calc_board.getSize() - 1))&& (calc_board.isCellFree( x + 1, y )))
          ||  
       ((y != (calc_board.getSize() - 1))&& (calc_board.isCellFree( x, y + 1 ) ))
      );  
    }
   

    public boolean cell_has_white_neighbours(int x , int y )    {
      
      return ( ((x != 0)&&(calc_board.isCellWhite( x- 1, y ) ))
          ||
       ((y != 0)&&(calc_board.isCellWhite( x, y - 1 )))
          ||  
       ((x != (calc_board.getSize() - 1))&& (calc_board.isCellWhite( x + 1, y )))
          ||  
       ((y != (calc_board.getSize() - 1))&& (calc_board.isCellWhite( x, y + 1 ) ))
      );  
    }
   
    public boolean cell_has_black_neighbours(int x , int y )    {
        
        return ( ((x != 0)&&(calc_board.isCellBlack( x- 1, y ) ))
            ||
         ((y != 0)&&(calc_board.isCellBlack( x, y - 1 )))
            ||  
         ((x != (calc_board.getSize() - 1))&& (calc_board.isCellBlack( x + 1, y )))
            ||  
         ((y != (calc_board.getSize() - 1))&& (calc_board.isCellBlack( x, y + 1 ) ))
        );  
      }
    
    
    /**
     * check if a group has liberties
     * 
     * @param group2check - the group to check
     * @return boolean weather the group has a liberty
     * 
     */
    public boolean hasGroupLibertie(int group2check ) {
        for (int xg = 0; xg < getBoardSize(); xg++)
            for (int yg = 0; yg < getBoardSize(); yg++)
                if ((groups[xg][yg]==group2check)&&(cell_has_liberty(xg,yg)))
                     return true; // if one of the stones in the group has a liberty -> return true because then the group has a liberty
        return false;  // found no stone in the group with a liberty 
    }
        
    public boolean isAreaGroupBlacks(int group2check ) {
    	if(group2check==-1) return false;
    	boolean res=false;
        for (int xg = 0; xg < getBoardSize(); xg++)
            for (int yg = 0; yg < getBoardSize(); yg++)
               if (area_groups[xg][yg]==group2check)
            	   if (cell_has_white_neighbours(xg,yg))
            		   return false;
            	   else
            		   res|=(cell_has_black_neighbours(xg,yg));
            		   
        return res;  // found no stone in the group with a liberty 
    }
   
    public boolean isAreaGroupWhites(int group2check ) {
    	if(group2check==-1) return false;
    	boolean res=false;
        for (int xg = 0; xg < getBoardSize(); xg++)
            for (int yg = 0; yg < getBoardSize(); yg++)
               if (area_groups[xg][yg]==group2check)
            	   if (cell_has_black_neighbours(xg,yg))
            		   return false;
            	   else
            		   res|=(cell_has_white_neighbours(xg,yg));
            		   
        return res;  // found no stone in the group with a liberty 
    }
       
    public void clear_calc_board() {
        for (byte x = 0; x < calc_board.getSize(); x++)
            for (byte y = 0; y < calc_board.getSize(); y++) 
                calc_board.setCellFree(x,y );
        apply_handicap();
    }
  
    /**
     * group the stones 
     * 
     * the result is written in groups[][]
     * 
     */
    
    public void buildGroups() {
        group_count=0;
        
        
        // reset groups
        for (int x = 0; x < calc_board.getSize(); x++)
            for (int y = 0; y < calc_board.getSize(); y++) {
                groups[x][y] = -1;
            }
        
        for (int x = 0; x < calc_board.getSize(); x++)
            for (int y = 0; y < calc_board.getSize(); y++) {
                if (!calc_board.isCellFree( x, y )) {

                    if (x > 0) {
                        if (!calc_board.areCellsEqual( x, y, x - 1, y )) {
                            group_count++;
                            groups[x][y] = group_count;
                        }
                        else
                            groups[x][y] = groups[x - 1][y];
                    }
                    else {
                        group_count++;
                        groups[x][y] = group_count;
                    }
                    
                    if (y > 0) {
                        if (calc_board.areCellsEqual( x, y, x , y-1 )) {
                            int from_grp=groups[x][y];
                            
                            for (int xg = 0; xg < calc_board.getSize(); xg++)
                                for (int yg = 0; yg < calc_board.getSize(); yg++)
                                    if (groups[xg][yg]==from_grp)
                                        groups[xg][yg]=groups[x][y-1];
                        }
                    }

                }
            }

    }

    
    int area_group_count=0;
    
    public void buildAreaGroups() {
        area_group_count=0;
                
        // reset groups
        for (int x = 0; x < calc_board.getSize(); x++)
            for (int y = 0; y < calc_board.getSize(); y++) {
            	   area_groups[x][y] = -1;
            	   area_assign[x][y] = 0;
            }
        
        for (byte x = 0; x < calc_board.getSize(); x++)
            for (byte y = 0; y < calc_board.getSize(); y++) {
                if (calc_board.isCellFree( x, y )) {

                    if (x > 0) {
                        if (!calc_board.areCellsEqual( x, y, (byte)( x - 1), y )) {
                        	area_group_count++;
                        	area_groups[x][y] = area_group_count;
                        }
                        else
                        	area_groups[x][y] = area_groups[x - 1][y];
                    }
                    else {
                    	area_group_count++;
                    	area_groups[x][y] = area_group_count;
                    }
                    
                    if (y > 0) {
                        if (calc_board.areCellsEqual( x, y, x , (byte)(y-1) )) {
                            int from_grp=area_groups[x][y];
                            
                            for (int xg = 0; xg < calc_board.getSize(); xg++)
                                for (int yg = 0; yg < calc_board.getSize(); yg++)
                                    if (area_groups[xg][yg]==from_grp)
                                    	area_groups[xg][yg]=area_groups[x][y-1];
                        }
                    }

                }
            }

        territory_black=0;
        territory_white=0;
        for (int x = 0; x < calc_board.getSize(); x++)
            for (int y = 0; y < calc_board.getSize(); y++) {
            	if (isAreaGroupWhites(area_groups[x][y]))
        			{ 
            		area_assign[x][y]=PLAYER_WHITE;
            		territory_white++;
        			}
            	else if (isAreaGroupBlacks(area_groups[x][y])) {
            		territory_black++;
            		area_assign[x][y]=PLAYER_BLACK;
            	}
        			
            }
        
    }

    
    /**
     * 
     *  detect if there are dead groups on the board
     *   
     * the cell with ignore_x and ignore_y is ignored - e.g. last move
     *  
    **/
    public boolean isDeadGroupOnBoard(byte ignore_x,byte ignore_y) {
                
        for (int grp=0;grp<=group_count;grp++)
        {
            if (groups[ignore_x][ignore_y]==grp)
                    continue;
            
            boolean grp_living=false;
            int grp_members=0;
            for (int xg = 0; xg < calc_board.getSize(); xg++)
                for (int yg = 0; yg < calc_board.getSize(); yg++)
                    if (groups[xg][yg]==grp)
                        {
                    	grp_members++;
                    	grp_living |= cell_has_liberty(xg,yg);
                        }
                        
            
            if ((!grp_living)&&(grp_members>0)) {
            	Log.d("gobandroid " ,"Grp living" + grp);
            	return true;
            	
            }
        }
        
        return false; // found no dead group
    }



    /** 
     * 
     * remove dead groups from the board - e.g. after a move 
     * 
     * the cell with ignore_x and ignore_y is ignored - e.g. last move
     * 
     * **/
    public void remove_dead(byte ignore_x,byte ignore_y) {
                
        for (int grp=0;grp<=group_count;grp++) // iterate over all groups
        {

            if (groups[ignore_x][ignore_y]==grp)
                    continue;
            
            boolean grp_living=false;
            for (int xg = 0; xg < calc_board.getSize(); xg++)
                for (int yg = 0; yg < calc_board.getSize(); yg++)
                    if (groups[xg][yg]==grp)
                        grp_living |= cell_has_liberty(xg,yg);
                        
            if (!grp_living) 
                for (int xg = 0; xg < calc_board.getSize(); xg++)
                    for (int yg = 0; yg < calc_board.getSize(); yg++)
                        if (groups[xg][yg]==grp)
                        	{
                        	
                        	if (calc_board.isCellBlack(xg, yg))
                        			captures_white++;
                        	else
                        			captures_black++;
                        	
                        	calc_board.setCellFree(xg,yg );
                        	}
            
        }

    }

    /** 
     * 
     * return if it's a handicap stone so that the view can visualize it
     * 
     * TODO: - check rename ( general marker ) 
     * 		 - check caching ( in arr cuz speed )
     * 
     * **/
    public boolean isPosHoschi(byte x,byte y) {
    	
    	if ((x==0)||(y==0)||((y+1)==getBoardSize())||((x+1)==getBoardSize()))
    		return false;
    	
    	switch(getBoardSize())
    	{
    	case 9:
    		return (((x%2)==0)&&((y%2)==0));
    	case 13:
    		return (((x%3)==0)&&((y%3)==0));
    	
    	case 19:
    		return (((x==9)&&(y==9))|| 
    				((x==3)&&(y==3)) ||
    				((x==15)&&(y==15)) ||
    				((x==3)&&(y==15)) ||
    				((x==15)&&(y==3)) ||
    				((x==9)&&(y==3)) ||
    				((x==3)&&(y==9)) ||
    				((x==9)&&(y==15))||
    				((x==15)&&(y==9)) );
    		
    		
    	default:	
    		return false;
    	}
    	
    }
    
    public GoBoard getVisualBoard() {
        return visual_board;
    }
    public GoBoard getCalcBoard() {
        return calc_board;
    }

    
    public boolean isLastActionPass() {
    	return last_action_was_pass;
    }

    public boolean isFinished() {
    	return game_finished;
    }
    
    public boolean isBlackToMove() {
    	return (act_player==PLAYER_BLACK);
    }
    
    public int getCapturesBlack() {
    	return captures_black;
    }
    
    public int getCapturesWhite() {
    	return captures_white;
    }
    
    public int getBoardSize() {
    	return calc_board.getSize(); // TODO cache?
    }

    public int getGroup(byte x,byte y) {
        return groups[x][y];
    }
    
    public void setNextPlayer() {
    	act_player=(act_player==PLAYER_BLACK)?PLAYER_WHITE:PLAYER_BLACK;
    }
    
}