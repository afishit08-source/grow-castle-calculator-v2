package com.growcalculate;
import android.os.Bundle;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.*;
import java.io.*;
import java.util.*;
public class MainActivity extends AppCompatActivity {
    JSONObject meta;
    JSONObject heroes;
    JSONObject towers;
    JSONObject progression;
    LinearLayout root;
    Spinner buildSpinner;
    EditText waveInput;
    TextView result;
    ArrayList<JSONObject> builds=new ArrayList<>();
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    TextView tv(String s,int size){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setPadding(dp(8),dp(5),dp(8),dp(5));
        return t;
    }
    String asset(String name)throws Exception{
        InputStream in=getAssets().open(name);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[8192];
        int n;
        while((n=in.read(b))!=-1)out.write(b,0,n);
        in.close();
        return out.toString("UTF-8");
    }
    JSONObject load(String name)throws Exception{return new JSONObject(asset(name));}
    long checkpoint(JSONObject o,int wave)throws Exception{
        int[] ws={1000,2500,5000,7500,10000};
        if(wave<=1000)return o.getInt("1000");
        if(wave>=10000)return o.getInt("10000");
        int low=1000;
        int high=2500;
        int lv=1000;
        int hv=2500;
        for(int i=0;i<ws.length-1;i++){
            if(wave>=ws[i]&&wave<=ws[i+1]){
                low=ws[i];
                high=ws[i+1];
                lv=o.getInt(String.valueOf(low));
                hv=o.getInt(String.valueOf(high));
                break;
            }
        }
        double p=(wave-low)/(double)(high-low);
        return Math.round(lv+(hv-lv)*p);
    }
    long scalable(JSONObject o,int wave)throws Exception{
        if(o.has("cap"))return o.getLong("cap");
        if(wave<=10000)return checkpoint(o,wave);
        double ratio=o.optDouble("post_10000_ratio",0.01);
        double value=o.getDouble("10000")+(wave-10000)*ratio;
        return Math.max(o.getLong("10000"),Math.round(value));
    }
    long castleLevel(int wave)throws Exception{
        JSONObject p=progression.getJSONObject("post_10000_ratio");
        if(wave<=10000){
            JSONObject o=new JSONObject();
            JSONArray a=progression.getJSONArray("checkpoints");
            for(int i=0;i<a.length();i++){
                JSONObject c=a.getJSONObject(i);
                o.put(String.valueOf(c.getInt("wave")),c.getInt("castle"));
            }
            return checkpoint(o,wave);
        }
        return Math.round(400+(wave-10000)*p.getDouble("castle"));
    }
    long townArcherLevel(int wave)throws Exception{
        JSONObject p=progression.getJSONObject("post_10000_ratio");
        if(wave<=10000){
            JSONObject o=new JSONObject();
            JSONArray a=progression.getJSONArray("checkpoints");
            for(int i=0;i<a.length();i++){
                JSONObject c=a.getJSONObject(i);
                o.put(String.valueOf(c.getInt("wave")),c.getInt("town_archer"));
            }
            return checkpoint(o,wave);
        }
        return Math.round(1000+(wave-10000)*p.getDouble("town_archer"));
    }
    String fmt(long n){
        if(n>=1000000000)return String.format(Locale.US,"%.2fB",n/1000000000.0);
        if(n>=1000000)return String.format(Locale.US,"%.2fM",n/1000000.0);
        if(n>=1000)return String.format(Locale.US,"%,d",n);
        return String.valueOf(n);
    }
    long levelForTower(String name,int wave)throws Exception{
        JSONObject all=towers.getJSONObject("towers");
        if(!all.has(name))return 0;
        return scalable(all.getJSONObject(name),wave);
    }
    long levelForHero(String name,int wave)throws Exception{
        JSONObject all=heroes.getJSONObject("heroes");
        if(!all.has(name))return 0;
        return scalable(all.getJSONObject(name),wave);
    }
    long levelForLeader(String name,int wave)throws Exception{
        JSONObject all=heroes.getJSONObject("leaders");
        if(!all.has(name))return 0;
        return scalable(all.getJSONObject(name),wave);
    }
    void section(String title){
        TextView t=tv(title,18);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(t);
    }
    void row(String name,String value){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView a=tv(name,14);
        TextView b=tv(value,14);
        a.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        b.setGravity(Gravity.RIGHT);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        r.addView(a);
        r.addView(b);
        root.addView(r);
    }
    void showBuild(int index,int wave){
        root.removeAllViews();
        JSONObject build=builds.get(index);
        try{
            section(build.getString("name"));
            row("Wave",fmt(wave));
            row("Style",build.getString("style"));
            section("CORE");
            row("Castle","Lv."+fmt(castleLevel(wave)));
            row("Town Archer","Lv."+fmt(townArcherLevel(wave)));
            section("LEADER");
            String leader=build.getString("leader");
            row(leader,"Lv."+fmt(levelForLeader(leader,wave)));
            section("HEROES 12/12");
            JSONArray hs=build.getJSONArray("heroes");
            for(int i=0;i<hs.length();i++){
                String h=hs.getString(i);
                row((i+1)+". "+h,"Lv."+fmt(levelForHero(h,wave)));
            }
            section("TOWERS");
            boolean stone=false;
            for(int i=0;i<hs.length();i++)if(hs.getString(i).equalsIgnoreCase("Stone"))stone=true;
            JSONArray ts=build.getJSONArray(stone?"towers_with_stone":"towers_normal");
            row("Active Tower Slots",String.valueOf(ts.length())+"/6");
            for(int i=0;i<ts.length();i++){
                String tower=ts.getString(i);
                row((i+1)+". "+tower,"Lv."+fmt(levelForTower(tower,wave)));
            }
            if(stone)row("Stone Bonus","+2 Tower Slots");
            section("TOWN 3/3");
            JSONArray town=build.getJSONArray("town");
            for(int i=0;i<town.length();i++)row((i+1)+".",town.getString(i));
            section("CASTLE PARTS 4/4");
            JSONArray cp=build.getJSONArray("castle_parts");
            for(int i=0;i<cp.length();i++)row((i+1)+".",cp.getString(i));
            section("TREASURES 5/5");
            JSONArray tr=build.getJSONArray("treasures");
            for(int i=0;i<tr.length();i++)row((i+1)+".",tr.getString(i));
        }catch(Exception e){
            row("Error",e.getMessage());
        }
    }
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{
            meta=load("meta_2026.json");
            heroes=load("hero_levels_2026.json");
            towers=load("tower_levels_2026.json");
            progression=load("progression.json");
        }catch(Exception e){
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            return;
        }
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(12),dp(12),dp(30));
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        TextView title=tv("GrowCalculate",25);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(title);
        top.addView(tv("Grow Castle 1.50.14 • Meta 2026",13));
        root.addView(top);
        waveInput=new EditText(this);
        waveInput.setHint("Wave");
        waveInput.setText("10000");
        waveInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(waveInput);
        buildSpinner=new Spinner(this);
        try{
            JSONArray bs=meta.getJSONArray("builds");
            for(int i=0;i<bs.length();i++)builds.add(bs.getJSONObject(i));
            ArrayList<String> names=new ArrayList<>();
            for(JSONObject x:builds)names.add(x.getString("name"));
            buildSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        }catch(Exception e){}
        root.addView(buildSpinner);
        Button calculate=new Button(this);
        calculate.setText("CALCULATE BUILD");
        root.addView(calculate);
        result=tv("",14);
        root.addView(result);
        calculate.setOnClickListener(v->{
            int wave=10000;
            try{
                wave=Integer.parseInt(waveInput.getText().toString().trim());
                if(wave<1)wave=1;
            }catch(Exception ignored){}
            showBuild(buildSpinner.getSelectedItemPosition(),wave);
        });
        scroll.addView(root);
        setContentView(scroll);
        showBuild(0,10000);
    }
}