package newhorizon.util.feature.cutscene.events.util;

import arc.Core;
import arc.func.Cons;
import arc.func.Prov;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.EntityGroup;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.io.TypeIO;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import newhorizon.expand.entities.EntityRegister;
import newhorizon.expand.entities.NHGroups;
import newhorizon.util.annotation.ClientDisabled;
import newhorizon.util.feature.cutscene.CutsceneEvent;
import newhorizon.util.func.NHFunc;
import newhorizon.util.func.OV_Pair;

import static newhorizon.util.Tool_JsonHandler.DEFAULT;
import static newhorizon.util.Tool_JsonHandler.TEAM_DEFAULT;

@ClientDisabled
public class AutoEventTrigger implements Entityc, Cloneable{
	public static final String SPEED_SCL_KEY = "speed-scl";
	
	public boolean added;
	public transient int id = 0; //ID is given after add;
	
	public Prov<Team> teamProv = null;
	public Team team = null;
	
	//TODO use Bits instead
	public Seq<OV_Pair<Item>> items = new Seq<>();
	public Seq<OV_Pair<UnitType>> units = new Seq<>();
	public Seq<OV_Pair<Block>> buildings = new Seq<>();
	
	public CutsceneEvent eventType;
	public String eventProv = "";
	
	public int minTriggerWave = 5;
	public float spacingBase = 120 * Time.toSeconds, spacingRand = 120 * Time.toSeconds;
	public boolean disposable = false;
	
	protected float spacing = 60;
	protected float reload;
	protected final Interval timer = new Interval(3);
	public float checkSpacing = 180f;
	
	public static float timeScale = 1;
	
	public static void setScale(float f){
		Log.info("Set: " + f);
		Core.settings.put(SPEED_SCL_KEY, f);
	}
	
	public static float getScale(){
		return Core.settings.getFloat(SPEED_SCL_KEY, 0.675f);
	}
	
	public AutoEventTrigger(){
		teamProv = () -> Vars.state.rules.defaultTeam;
		eventType = CutsceneEvent.NULL_EVENT;
	}
	
	public AutoEventTrigger setEvent(CutsceneEvent event){
		eventType = event;
		return this;
	}
	
	
	public AutoEventTrigger modify(Cons<AutoEventTrigger> modifier){
		modifier.get(this);
		return this;
	}
	
	public boolean meet(Team team){
		if(Vars.state.rules.waves && minTriggerWave > Vars.state.wave)return false;
		
		Teams.TeamData data = Vars.state.teams.get(team);
		if(!Vars.state.teams.isActive(team) || data.noCores())return false;
		
		CoreBlock.CoreBuild core = team.core();
		for(OV_Pair<Item> stack : items)if(stack.value > core.items().get(stack.item))return false;
		
		for(OV_Pair<UnitType> pair : units)if(pair.value > data.countType(pair.item))return false;
		
		for(OV_Pair<Block> pair : buildings)if(pair.value > Groups.build.count(b -> b.team == team && b.block == pair.item))return false;
		
		return true;
	}
	
	public Team team(){
		return teamProv == null ? (team == null ? Team.derelict : team) : teamProv.get();
	}
	
	public boolean meet(){
		return meet(team());
	}
	
	@Override
	public void update(){
		if(team().cores().isEmpty()){
			remove();
			return;
		}
		
		if(CutsceneEvent.inValidEvent(eventType))remove();
		
		reload += Time.delta * timeScale;
		
		if(reload > spacing && !Vars.net.client() && timer.get(checkSpacing) && meet()){
			eventType.setup().setEventProv(eventProv);
			reload = 0;
			spacing = spacingBase + NHFunc.rand(id).random(spacingRand);
			
			if(disposable)remove();
		}
	}
	
	public float getSpacing(){
		return spacing;
	}
	
	public float getReload(){
		return reload;
	}
	
	@Override public void read(Reads read){
		int teamID = read.i();
		if(teamID < 0){
			teamProv = DEFAULT;
		}else{
			team = Team.get(teamID);
		}
		
		minTriggerWave = read.i();
		spacingBase = read.f();
		spacingRand = read.f();
		spacing = read.f();
		reload = read.f();
		disposable = read.bool();
		
		eventProv = read.str();
		
		int itemsS = read.i();
		int unitsS = read.i();
		int buildingsS = read.i();
		items = new Seq<>(itemsS);
		for(int i = 0; i < itemsS; i++)items.add(new OV_Pair<>(TypeIO.readItem(read), read.i()));
		units = new Seq<>(unitsS);
		for(int i = 0; i < unitsS; i++)units.add(new OV_Pair<>(TypeIO.readUnitType(read), read.i()));
		buildings = new Seq<>(buildingsS);
		for(int i = 0; i < buildingsS; i++)buildings.add(new OV_Pair<>(TypeIO.readBlock(read), read.i()));
		
		
		if(!eventProv.isEmpty()){
			eventType = CutsceneEvent.construct(eventProv);
		}else eventType = CutsceneEvent.readEvent(read);
	}
	
	@Override public void write(Writes write){
		if(team == null){
			write.i(TEAM_DEFAULT);
		}else write.i(team.id);
		
		write.i(minTriggerWave);
		write.f(spacingBase);
		write.f(spacingRand);
		write.f(spacing);
		write.f(reload);
		write.bool(disposable);
		
		write.str(eventProv);
		
		write.i(items.size);
		write.i(units.size);
		write.i(buildings.size);
		for(int i = 0; i < items.size; i++){
			TypeIO.writeItem(write, items.get(i).item);
			write.i(items.get(i).value);
		}
		for(int i = 0; i < units.size; i++){
			TypeIO.writeUnitType(write, units.get(i).item);
			write.i(units.get(i).value);
		}
		for(int i = 0; i < buildings.size; i++){
			TypeIO.writeBlock(write, buildings.get(i).item);
			write.i(buildings.get(i).value);
		}
		
		if(eventProv.isEmpty())CutsceneEvent.writeEvent(eventType, write);
	}
	
	public void remove(){
		Groups.all.remove(this);
		NHGroups.autoEventTrigger.remove(this);
		
		added = false;
	}
	
	@Override
	@ClientDisabled
	public void add(){
		if(!Vars.net.client() && !added){
			id = EntityGroup.nextId();
			
			Groups.all.add(this);
			NHGroups.autoEventTrigger.add(this);
			
			spacing = spacingBase + NHFunc.rand(id).random(spacingRand);
			reload = 0;
			
			added = false;
		}
	}
	
	@Override public boolean isLocal(){
		return false;
	}
	@Override public boolean isRemote(){
		return true;
	}
	@Override public boolean isNull(){ return false; }
	@Override public <T extends Entityc> T self(){ return (T)this; }
	@Override public <T> T as(){ return (T)this; }

	@Override public boolean isAdded(){ return added; }
	
	@Override public boolean serialize(){ return true; }
	
	@Override public int classId(){return EntityRegister.getID(AutoEventTrigger.class);}
	@Override public void afterRead(){ }
	@Override public int id(){return id; }
	@Override public void id(int id){ this.id = id; }
	
	@Override
	public String toString(){
		return "AutoEventTrigger{" + "id=" + id + ", event=" + eventType + '}';
	}
	
	public AutoEventTrigger copy(){
		try{
			AutoEventTrigger clone = (AutoEventTrigger)super.clone();
			
			clone.added = false;
			clone.id = 0;
			
			return clone;
		}catch(CloneNotSupportedException ignored){return null;}
	}
	
	public String desc(){
		StringBuilder stringBuilder = new StringBuilder();
		
		stringBuilder.append("[accent]");
		stringBuilder.append(eventType.type());
		stringBuilder.append('\n');
		
		if(items.size == 0 && units.size == 0 && buildings.size == 0){
			stringBuilder.append("No Requirements");
		}else{
			stringBuilder.append("Requirements:\n");
			
			if(items.size != 0)stringBuilder.append("[gray]<Items>\n");
			
			for(OV_Pair<Item> item : items){
				stringBuilder.append("    [lightgray]|  ");
				stringBuilder.append(item.item.localizedName);
				stringBuilder.append(" *[accent]");
				stringBuilder.append(item.value);
				stringBuilder.append('\n');
			}
			
			if(units.size != 0)stringBuilder.append("[gray]<Units>\n");
			
			for(OV_Pair<UnitType> unit : units){
				stringBuilder.append("    [lightgray]|  ");
				stringBuilder.append(unit.item.localizedName);
				stringBuilder.append(" *[accent]");
				stringBuilder.append(unit.value);
				stringBuilder.append('\n');
			}
			
			if(buildings.size != 0)stringBuilder.append("[gray]<Blocks>:\n");
			
			for(OV_Pair<Block> block : buildings){
				stringBuilder.append("    [lightgray]|  ");
				stringBuilder.append(block.item.localizedName);
				stringBuilder.append(" *[accent]");
				stringBuilder.append(block.value);
				stringBuilder.append('\n');
			}
		}
		
		return stringBuilder.toString();
	}
	
}
