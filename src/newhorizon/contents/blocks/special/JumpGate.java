package newhorizon.contents.blocks.special;


import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.gen.Icon;
import mindustry.game.Team;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.logic.Ranged;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Cicon;
import mindustry.ui.ItemDisplay;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.meta.Stat;
import newhorizon.func.Functions;
import newhorizon.contents.items.NHItems;

import java.util.Iterator;

import static newhorizon.func.Functions.*;
import static mindustry.Vars.*;
import static mindustry.Vars.state;

public class JumpGate extends Block {
    public float spawnDelay = 5f;
    public float spawnReloadTime = 180f;
    public float spawnRange = 120f;
    public float range = 200f;
    public float inComeVelocity = 5f;
    public TextureRegion
            pointerRegion,
            arrowRegion,
            bottomRegion,
            armorRegion;
    public Color baseColor;
    public final Seq<UnitSet> calls = new Seq<>();

    public float squareStroke = 2f;

    public JumpGate(String name){
        super(name);
        update = true;
        configurable = true;
        solid = true;
        hasPower = true;
        this.category = Category.units;
    }

    public JumpGate(String name, UnitSet... sets){
        this(name);
        addSets(sets);
    }


    public void addSets(UnitSet... sets){
        calls.addAll(sets);
    }

    @Override
    public void init(){
        super.init();
        if(calls.isEmpty()) throw new IllegalArgumentException("Seq @calls is [red]EMPTY[].");
    }

    @Override
    public void setStats() {
        super.setStats();
        this.stats.add(Stat.output, (t) -> {
            t.row().add("Summon Types:", Styles.techLabel).left().pad(OFFSET).row();
            for(UnitSet set : calls) {
                t.table(Tex.button, t2 -> {
                    t2.table(Tex.button, table2 -> table2.image(set.type.icon(Cicon.large)).size(LEN).center()).left().size(LEN + OFFSET * 1.5f).pad(OFFSET);

                    t2.pane(table2 -> {
                        table2.add("[lightgray]Summon: [accent]" + set.type.localizedName + "[lightgray]; Level: [accent]" + set.level + "[].").left().row();
                        table2.add("[lightgray]NeededTime: [accent]" + format(set.costTime() / 60) + "[lightgray] sec[]").left().row();
                    }).size(LEN * 6f, LEN).left().pad(OFFSET);

                    t2.table(table2 -> {
                        table2.button(Icon.infoCircle, Styles.clearTransi, () -> showInfo(set, "[accent]Caution[]: Summon needs building.")).size(LEN);
                    }).height(LEN + OFFSET).growX().left().pad(OFFSET);
                }).grow().padBottom(OFFSET / 2).row();
            }
        });
    }

    public void showInfo(UnitSet set, String textExtra){
        BaseDialog dialogIn = new BaseDialog("More Info");
        dialogIn.addCloseListener();
        dialogIn.cont.margin(15f);
        dialogIn.cont.table(Tex.button, t -> t.image(set.type.icon(Cicon.xlarge)).center()).grow().row();
        dialogIn.cont.add("<<[accent] " + set.type.localizedName + " []>>").row();
        dialogIn.cont.add("[lightgray]Call: [accent]" + set.type.localizedName + "[lightgray]; Level: [accent]" + set.level + "[].").left().padLeft(OFFSET).row();
        dialogIn.cont.add("[lightgray]BuildNeededTime: [accent]" + format(set.costTime() / 60) + "[lightgray] sec[]").left().padLeft(OFFSET).row();
        dialogIn.cont.pane(table -> {
            int index = 0;
            for(ItemStack stack : set.requirements()){
                if(index % 5 == 0)table.row();
                table.add(new ItemDisplay(stack.item, stack.amount, false)).padRight(5).left();
                index ++;
            }
        }).left().padLeft(OFFSET).row();
        if(!textExtra.equals(""))dialogIn.cont.add(textExtra).left().padLeft(OFFSET).row();
        dialogIn.cont.image().fillX().pad(2).height(4f).color(Pal.accent);
        dialogIn.cont.row();
        dialogIn.cont.button("@back", Icon.left, dialogIn::hide).size(LEN * 2.5f, LEN).pad(OFFSET / 3);
        dialogIn.show();
    }

    @Override
    public void setBars() {
        super.setBars();
        bars.add("progress",
                (JumpGateBuild entity) -> new Bar(
                        () -> "Progress",
                        () -> Pal.power,
                        () -> entity.getSet() == null ? 0 : entity.buildReload / entity.getSet().costTime()
                )
        );
    }

    @Override
    public void load(){
        super.load();
        pointerRegion = Core.atlas.find(this.name + "-pointer");
        arrowRegion = Core.atlas.find(this.name + "-arrow");
        bottomRegion = Core.atlas.find(this.name + "-bottom");
        armorRegion = Core.atlas.find(this.name + "-armor");
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        Color color = baseColor == null ? Pal.accent : baseColor;
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, color);
    }

    @Override
    protected TextureRegion[] icons() {
        return this.teamRegion.found() && this.minfo.mod == null ? new TextureRegion[]{this.bottomRegion, this.region, this.teamRegions[Team.sharded.id], armorRegion} : new TextureRegion[]{this.bottomRegion, this.region, armorRegion};
    }

    public class JumpGateBuild extends Building implements Ranged {
        public Color baseColor(){
            return baseColor == null ? this.team().color : baseColor;
        }
        public int spawnID = -1;
        public int spawnPOS = -1;
        public int spawns = 1;

        public float buildReload = 0f;

        @Override
        public void updateTile(){
            super.updateTile();
            if(isCalling()){
                this.buildReload += efficiency() * (state.rules.infiniteResources ? Float.MAX_VALUE : 1);
                if(this.buildReload >= getSet().costTime()){
                    spawn(getSet());
                }
            }
        }

        public UnitType getType(){ return calls.get(spawnID).type; }
        
        public void setSpawnPos(int pos){ this.spawnPOS = pos; }
        
        public Building getSpawnPos(){ return Vars.world.build(spawnPOS); }

        public UnitSet getSet(){
            if(spawnID < 0 || spawnID >= calls.size)return null;
            return calls.get(spawnID);
        }

        @Override
        public void drawConfigure() {
            Drawf.dashCircle(x, y, range(), baseColor());
            Draw.color(baseColor());
            Lines.square(x, y, block().size * tilesize / 2f + 1.0f);
            if(getSpawnPos() != null) {
                Building target = getSpawnPos();
                Draw.alpha(0.3f);
                Fill.square(target.x, target.y, target.block.size / 2f * tilesize);
                Draw.alpha(1f);
                Drawf.dashCircle(target.x, target.y, spawnRange, baseColor());
                Draw.color(baseColor());
                Lines.square(target.x, target.y, target.block().size * tilesize / 2f + 1.0f);

                Lines.stroke(3f, Pal.gray);
                Lines.line(x, y, target.x, target.y);
                Fill.square(x, y, 4.5f, 45);
                Fill.square(target.x, target.y, 4.5f, 45);

                Lines.stroke(1f, baseColor());
                Lines.line(x, y, target.x, target.y);
                Fill.square(x, y, 3.3f, 45);
                Fill.square(target.x, target.y, 3.3f, 45);
            }else Drawf.dashCircle(x, y, spawnRange, baseColor());
            Draw.reset();
        }

        @Override
        public boolean onConfigureTileTapped(Building other) {
            if (this == other || this.spawnPOS == other.pos()) {
                setSpawnPos(-1);
                return false;
            }
            if (other.within(this, range())) {
                setSpawnPos(other.pos());
                return false;
            }
            return true;
        }
        
        @Override
        public void buildConfiguration(Table table) {
            BaseDialog dialog = new BaseDialog("Call");
            dialog.addCloseListener();

            dialog.cont.table(t -> {
                for(UnitSet set : calls) {
                    t.table(Tex.button, t2 -> {
                        t2.table(Tex.button, table2 -> table2.image(set.type.icon(Cicon.large)).size(LEN).center()).left().size(LEN + OFFSET * 1.5f).pad(OFFSET);

                        t2.pane(table2 -> {
                            table2.add("[lightgray]Call: [accent]" + set.type.localizedName + "[lightgray]; Level: [accent]" + set.level + "[].").left().row();
                            table2.add("[lightgray]NeededTime: [accent]" + format(set.costTime() / 60) + "[lightgray] sec[]").left().row();
                        }).size(LEN * 6f, LEN).left().pad(OFFSET);

                        t2.table(Tex.button, table2 -> {
                            table2.button(Icon.infoCircle, Styles.clearTransi, () -> showInfo(set, "[lightgray]CanCall?: " + getJudge(canSpawn(set)) + "[]")).size(LEN);
                            table2.button(Icon.add, Styles.clearPartiali, () -> startBuild(set)).size(LEN).disabled(b -> !canSpawn(set));
                        }).height(LEN + OFFSET).growX().left().pad(OFFSET);
                    }).grow().padBottom(OFFSET / 2).row();
                }

                t.image().fillX().height(OFFSET / 2).color(Pal.accent).pad(OFFSET).row();
                t.button("@back", Icon.left, dialog::hide).size(210.0F, 64.0F).row();
            }).fill();

            table.button("Spawn", Icon.add, dialog::show).size(LEN * 5, LEN);
        }

        @Override
        public void draw(){
            Draw.rect(bottomRegion, x, y);
            super.draw();
            Draw.rect(armorRegion, x, y);
            Draw.reset();
            Draw.z(Layer.bullet);
            if(efficiency() > 0){
                Lines.stroke(squareStroke, baseColor());
                float rot = Time.time * efficiency();
                Lines.square(x, y, block.size * tilesize / 2.5f, -rot);
                Lines.square(x, y, block.size * tilesize / 2f, rot);
                for(int i = 0; i < 4; i++){
                    float length = tilesize * block().size / 2f + 8f;
                    Tmp.v1.trns(i * 90 + rot, -length);
                    Draw.rect(arrowRegion,x + Tmp.v1.x,y + Tmp.v1.y,i * 90 + 90 + rot);
                    float sin = Mathf.absin(Time.time, 16f, tilesize);
                    length = tilesize * block().size / 2f + 3 + sin;
                    float signSize = 0.75f + Mathf.absin(Time.time + 8f, 8f, 0.15f);
                    Tmp.v1.trns(i * 90, -length);
                    Draw.rect(pointerRegion, x + Tmp.v1.x,y + Tmp.v1.y, pointerRegion.width * Draw.scl * signSize, pointerRegion.height * Draw.scl * signSize, i * 90 + 90);
                }
                Draw.color();
            }
            Draw.reset();
        }
        @Override public float range(){return range;}
        @Override
        public void write(Writes write) {
            write.i(this.spawnID);
            write.i(this.spawnPOS);
            write.i(this.spawns);
            write.f(this.buildReload);
        }
        @Override
        public void read(Reads read, byte revision) {
            this.spawnID = read.i();
            this.spawnPOS = read.i();
            this.spawns = read.i();
            this.buildReload = read.f();
        }

        public boolean isCalling(){ return spawnID >= 0; }

        public boolean coreValid() { return this.team.core() != null && this.team.core().items != null && !this.team.core().items.empty(); }

        public void consumeItems(){
            if(state.rules.infiniteResources)return;
            if(coreValid())this.team.core().items.remove(getSet().requirements());
        }

        public boolean canSpawn(UnitSet set) {
            CoreBlock.CoreBuild core = this.team.core();
            return Units.canCreate(team, set.type) && (state.rules.infiniteResources ||
                (coreValid() && ! isCalling() && core.items.has(set.requirements() )
            ));
        }

        public void startBuild(UnitSet set){
            this.spawnID = calls.indexOf(set);
            ui.showInfoPopup("[accent]<<Caution>>[]:Team : " + team.name + "[] starts summon level[accent] " + set.level + " []fleet.", 8f, 0, 20, 20, 20, 20);
        }

        public void spawn(UnitSet set){
            if(!isValid() || !Units.canCreate(team, set.type))return;
            consumeItems();
            this.spawns = set.callIns;
            this.buildReload = 0;
            this.spawnID = -1;
            float Sx, Sy;
            int spawnNum = set.callIns;
            if(team.data().countType(set.type) + spawnNum > Units.getCap(team)){
                spawnNum = Units.getCap(team) - team.data().countType(set.type);
            }

            if(getSpawnPos() != null) {
                Building target = getSpawnPos();
                Sx = target.x;
                Sy = target.y;
            }else{
                Sx = x;
                Sy = y;
            }

            Functions.spawnUnit(this, Sx, Sy, spawnNum, set.level, spawnRange, spawnReloadTime, spawnDelay, inComeVelocity, set.type, baseColor());
        }
    }

    public static class UnitSet{
        public int level;
        public final Seq<ItemStack> requirements = new Seq<>(ItemStack.class);
        public UnitType type;
        public float costTime = 60f;
        public int callIns = 5;

        public UnitSet(){ this(1, UnitTypes.alpha, 0, 5); }

        public UnitSet(int level, UnitType type){
            this.type = type;
            this.level = level;
            this.requirements.add(new ItemStack(NHItems.emergencyReplace, 0));
        }

        public UnitSet(int level, UnitType type, float costTime, int callIns, ItemStack... requirements){
            this.type = type;
            this.level = level;
            this.costTime = costTime;
            this.callIns = callIns;
            this.requirements.addAll(requirements);
        }

        public float costTime(){return /*Vars.state.rules.infiniteResources ? 0f :*/ costTime * (1 + Vars.state.rules.unitBuildSpeedMultiplier);}
        public ItemStack[] requirements(){ return requirements.toArray(); }
    }
}