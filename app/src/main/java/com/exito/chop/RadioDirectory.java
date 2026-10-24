package com.exito.chop;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
/** Radio Browser supplies discovery, not a guaranteed right to record or rebroadcast. */
final class RadioDirectory {
 static final String[] MIRRORS={"https://de1.api.radio-browser.info","https://nl1.api.radio-browser.info","https://at1.api.radio-browser.info"};
 static final class Station {
  final String id,name,country,codec,stream,homepage,tags;final int votes;
  Station(String id,String name,String country,String codec,String stream,String homepage,String tags,int votes){this.id=id;this.name=name;this.country=country;this.codec=codec;this.stream=stream;this.homepage=homepage;this.tags=tags;this.votes=votes;}
  public String toString(){return name+" · "+country+" · "+codec;}
 }
 static String encode(String term)throws UnsupportedEncodingException{return URLEncoder.encode(term,"UTF-8");}
 static String path(String mode,String query,String country,String genre)throws Exception{
  String uri="/json/stations/search?limit=60&hidebroken=true&order=votes&reverse=true";
  if("popular".equals(mode))return "/json/stations/topvote/75?hidebroken=true";
  if(!query.trim().isEmpty())uri+="&name="+encode(query.trim());
  if(!country.trim().isEmpty())uri+="&countrycode="+encode(country.trim().toUpperCase(Locale.ROOT));
  if(!genre.trim().isEmpty())uri+="&tag="+encode(genre.trim());
  return uri;
 }
 static List<Station> parse(String json)throws Exception{
  JSONArray list=new JSONArray(json);ArrayList<Station> result=new ArrayList<>();HashSet<String> seen=new HashSet<>();
  for(int i=0;i<list.length();i++){
   JSONObject o=list.optJSONObject(i);if(o==null)continue;
   String url=o.optString("url_resolved",o.optString("url",""));
   String id=o.optString("stationuuid","");
   if(id.isEmpty()||!seen.add(id)||!safe(url)||!o.optBoolean("lastcheckok",true))continue;
   result.add(new Station(id,o.optString("name","Emisora"),o.optString("countrycode",""),o.optString("codec","?"),url,o.optString("homepage",""),o.optString("tags",""),o.optInt("votes",0)));
  }return result;
 }
 static boolean safe(String uri){try{URI u=new URI(uri);String scheme=u.getScheme();return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))&&u.getHost()!=null&&!u.getHost().isEmpty();}catch(Exception ex){return false;}}
 static List<Station> findByIds(String ids)throws Exception{
  if(!ids.matches("[a-fA-F0-9,-]{1,1500}"))throw new IOException("Identificadores de emisora no válidos");
  Exception last=null;for(String base:MIRRORS){HttpURLConnection c=null;try{
   c=(HttpURLConnection)new URL(base+"/json/stations/byuuid?uuids="+ids).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);
   c.setRequestProperty("User-Agent","ChopGO/0.4.3 (Android radio sampler)");
   if(c.getResponseCode()!=200)throw new IOException("Directorio HTTP "+c.getResponseCode());
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("Lista demasiado grande");out.write(b,0,n);}
    return parse(out.toString("UTF-8"));}
  }catch(Exception e){last=e;}finally{if(c!=null)c.disconnect();}}
  throw last==null?new IOException("Sin respuesta"):last;
 }
 static List<Station> find(String mode,String query,String country,String genre)throws Exception{
  String route=path(mode,query,country,genre);Exception last=null;
  for(String base:MIRRORS){HttpURLConnection c=null;try{
   c=(HttpURLConnection)new URL(base+route).openConnection();c.setConnectTimeout(7500);c.setReadTimeout(11000);
   c.setRequestProperty("User-Agent","ChopGO/0.4.3 (Android radio sampler)");c.setRequestProperty("Accept","application/json");
   if(c.getResponseCode()!=200)throw new IOException("Directorio HTTP "+c.getResponseCode());
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("Lista demasiado grande");out.write(b,0,n);}
    return parse(out.toString("UTF-8"));
   }
  }catch(Exception ex){last=ex;}finally{if(c!=null)c.disconnect();}}
  throw last==null?new IOException("Directorio de radios no disponible"):last;
 }
}
