package javabot.controllers;

import java.awt.Point;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javabot.JavaBot;
import javabot.models.Unit;
import javabot.types.UnitType.UnitTypes;

public class BuildManager implements Manager {
	private static BuildManager instance = null;
	
	//list of buildings. key represents ordinal of UnitTypes, value list of that unit
	private HashMap<Integer, HashSet<Integer>> buildings = new HashMap<Integer, HashSet<Integer>>();
	
	private BuildManager() {
	}
	
	public static BuildManager getInstance() {
		if(instance == null) {
			instance = new BuildManager();
		}
		return instance;
	}
	
	public void reset() {
		buildings = new HashMap<Integer, HashSet<Integer>>();
	}
	
	@Override
	public void act() {
		// Build pylons if we are low on supply (if free supply is less than 3).
		if (((JavaBot.bwapi.getSelf().getSupplyTotal() - JavaBot.bwapi.getSelf().getSupplyUsed())/2) < 3) {
			// Check if we have enough minerals,
			if (JavaBot.bwapi.getSelf().getMinerals() >= 100) {
				// try to find the worker near our home position
				int worker = getNearestUnit(UnitTypes.Protoss_Probe.ordinal(), JavaBot.homePositionX, JavaBot.homePositionY);
				if (worker != -1) {
					// if we found him, try to select appropriate build tile position for supply depot (near our home base)
					Point buildTile = getBuildTile(worker, UnitTypes.Protoss_Pylon.ordinal(), JavaBot.homePositionX, JavaBot.homePositionY);
					// if we found a good build position, and we aren't already constructing a Supply Depot, 
					// order our worker to build it
					if ((buildTile.x != -1) && (!weAreBuilding(UnitTypes.Protoss_Pylon.ordinal()))) {
						JavaBot.bwapi.build(worker, buildTile.x, buildTile.y, UnitTypes.Protoss_Pylon.ordinal());
					}
				}
			}
		}

		//build first gateway
		if(getBuildingCount(UnitTypes.Protoss_Gateway.ordinal()) <= 2) {
			if (JavaBot.bwapi.getSelf().getSupplyTotal() > 12) {
				if(JavaBot.bwapi.getSelf().getMinerals() >= 150) {
					int worker = getNearestUnit(UnitTypes.Protoss_Probe.ordinal(), JavaBot.homePositionX, JavaBot.homePositionY);
					if (worker != -1) {
						// if we found him, try to select appropriate build tile position for supply depot (near our home base)
						Point buildTile = getBuildTile(worker, UnitTypes.Protoss_Gateway.ordinal(), JavaBot.homePositionX, JavaBot.homePositionY);
						// if we found a good build position, and we aren't already constructing a Supply Depot, 
						// order our worker to build it
						if ((buildTile.x != -1) && (!weAreBuilding(UnitTypes.Protoss_Gateway.ordinal()))) {
							JavaBot.bwapi.build(worker, buildTile.x, buildTile.y, UnitTypes.Protoss_Gateway.ordinal());
						}
					}
				}
			}
		}
	}
	
	/**
	 * Returns count of requested building
	 * @param type key to search for 
	 * @return
	 */
	public int getBuildingCount(int type) {
		return buildings.containsKey(type) ? buildings.get(type).size() : 0;
	}
	
	// Returns true if we are currently constructing the building of a given type.
	public static boolean weAreBuilding(int buildingTypeID) {
		for (Unit unit : JavaBot.bwapi.getMyUnits()) {
			if ((unit.getTypeID() == buildingTypeID) && (!unit.isCompleted()))
				return true;
			if (JavaBot.bwapi.getUnitType(unit.getTypeID()).isWorker() && unit.getConstructingTypeID() == buildingTypeID)
				return true;
		}
		return false;
	}
	
	// Returns the id of a unit of a given type, that is closest to a pixel position (x,y), or -1 if we
    // don't have a unit of this type
    public int getNearestUnit(int unitTypeID, int x, int y) {
    	int nearestID = -1;
	    double nearestDist = 9999999;
	    for (Unit unit : JavaBot.bwapi.getMyUnits()) {
	    	if ((unit.getTypeID() != unitTypeID) || (!unit.isCompleted())) continue;
	    	double dist = Math.sqrt(Math.pow(unit.getX() - x, 2) + Math.pow(unit.getY() - y, 2));
	    	if (nearestID == -1 || dist < nearestDist) {
	    		nearestID = unit.getID();
	    		nearestDist = dist;
	    	}
	    }
	    return nearestID;
    }	
	
	// Returns the Point object representing the suitable build tile position
	// for a given building type near specified pixel position (or Point(-1,-1) if not found)
	// (builderID should be our worker)
	public static Point getBuildTile(int builderID, int buildingTypeID, int x, int y) {
		Point ret = new Point(-1, -1);
		int maxDist = 3;
		int stopDist = 40;
		int tileX = x/32; int tileY = y/32;
		
		// Refinery, Assimilator, Extractor
		if (JavaBot.bwapi.getUnitType(buildingTypeID).isRefinery()) {
			for (Unit n : JavaBot.bwapi.getNeutralUnits()) {
				if ((n.getTypeID() == UnitTypes.Resource_Vespene_Geyser.ordinal()) && 
						( Math.abs(n.getTileX()-tileX) < stopDist ) &&
						( Math.abs(n.getTileY()-tileY) < stopDist )
						) return new Point(n.getTileX(),n.getTileY());
			}
		}
		
		while ((maxDist < stopDist) && (ret.x == -1)) {
			for (int i=tileX-maxDist; i<=tileX+maxDist; i++) {
				for (int j=tileY-maxDist; j<=tileY+maxDist; j++) {
					if (JavaBot.bwapi.canBuildHere(builderID, i, j, buildingTypeID, false)) {
						// units that are blocking the tile
						boolean unitsInWay = false;
						for (Unit u : JavaBot.bwapi.getAllUnits()) {
							if (u.getID() == builderID) continue;
							if ((Math.abs(u.getTileX()-i) < 4) && (Math.abs(u.getTileY()-j) < 4)) unitsInWay = true;
						}
						if (!unitsInWay) {
							ret.x = i; ret.y = j;
							return ret;
						}
						
						// psi power for Protoss (this seems to work out of the box)
						if (JavaBot.bwapi.getUnitType(buildingTypeID).isRequiresPsi()) {}
					}
				}
			}
			maxDist += 2;
		}
		
		if (ret.x == -1)
			JavaBot.bwapi.printText("Unable to find suitable build position for "+JavaBot.bwapi.getUnitType(buildingTypeID).getName());
		return ret;
	}

	@Override
	public void gameUpdate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void assignUnit(Unit unit) {
		if (!buildings.containsKey(unit.getTypeID()))
			buildings.put(unit.getTypeID(), new HashSet<Integer>());
		buildings.get(unit.getTypeID()).add(unit.getID());
	}

	@Override
	public int removeUnit(int unitId) {
		// TODO Auto-generated method stub
		return -1;
	}

}
