/*
   Copyright (C) 2014 Iliya Romm
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License


   Based on the work of George MacKerron @ https://github.com/jawj/OverlappingMarkerSpiderfier

   Attempt was made to stick to the original method and variable names and to include most of the original comments.
   Last-modified date: 20/02/14

   Note: this version is intended to work with android-maps-extensions. Adaptation to the standard
         Google Maps API should be straightforward.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.graphics.Color;
import android.graphics.Point;
import android.util.Log;
import com.androidmapsextensions.*;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public class OverlappingMarkerSpiderfier { /** Corresponds to line 12 of original code*/

    private static final String VERSION = "0.3.3"; //version of original code.
    private static final String LOGTAG = "MarkerSpiderfier";
    private static final double TWO_PI = Math.PI * 2;
    private static final double RADIUS_SCALE_FACTOR = 10; // TODO: needs to be computed according to the device px density & zoom lvl

    // Passable params' names
    private static final String ARG_KEEP_SPIDERFIED = "keepSpiderfied";
    private static final String ARG_MARK_WONT_HIDE  = "markersWontHide";
    private static final String ARG_MARK_WONT_MOVE  = "markersWontMove";
    private static final String ARG_NEARBY_DISTANCE = "nearbyDistance";
    private static final String ARG_CS_SWITCHOVER   = "circleSpiralSwitchover";
    private static final String ARG_LEG_WEIGHT      = "legWeight";

    private GoogleMap gm;
//  ge = gm.event // This is simpler in android, as events aren't "general"
    private int mt; // map type
    private Projection proj;

    /////// START OF PASSABLE PARAMS ///////

    private boolean keepSpiderfied = false;
    private boolean markersWontHide = false;
    private boolean markersWontMove = false;

    private int nearbyDistance = 20;        // spiderfy markersInCluster within this range of the one clicked, in px
    private int circleSpiralSwitchover = 9; // show spiral instead of circle from this marker count upwards
                                            // 0 -> always spiral; Infinity -> always circle
    private float legWeight = 3F;

    /////// END OF PASSABLE PARAMS ////////

    private int circleFootSeparation = 23; // related to circumference of circles
    private double circleStartAngle = TWO_PI / 12;
    private int spiralFootSeparation = 26; // related to size of spiral (experiment!)
    private int spiralLengthStart = 11;    // ditto
    private int spiralLengthFactor = 4;    // ditto

    private int spiderfiedZIndex = 1000;   // ensure spiderfied markersInCluster are on top
    private int usualLegZIndex = 0;       // for legs
    private int highlightedLegZIndex = 20; // ensure highlighted leg is always on top

    private class _omsData{
        private LatLng usualPosition;
        private Polyline leg;

        public LatLng getUsualPosition() {
            return usualPosition;
        }

        public Polyline getLeg() {
            return leg;
        }

        public _omsData leg(Polyline newLeg){
            if(leg!=null)
                leg.remove();
            leg=newLeg;
            return this; // return self, for chaining
        }
        public _omsData usualPosition(LatLng newUsualPos){
            usualPosition=newUsualPos;
            return this; // return self, for chaining
        }


    }

    private class MarkerData{
        public Marker marker;
        public Point markerPt;
        public boolean willSpiderfy = false;
        public MarkerData(Marker mark, Point pt){
            marker = mark;
            markerPt = pt;
        }
        public MarkerData(Marker mark, Point pt, boolean spiderfication){
            marker = mark;
            markerPt = pt;
            willSpiderfy = spiderfication;
        }
    }

    private class LegColor{

        private final int type_satellite;
        private final int type_normal; // in the javascript version this is known as "roadmap"

        public LegColor(int set, int road){
            type_satellite = set;
            type_normal = road;
        }

        public LegColor(String set, String road){
            type_satellite = Color.parseColor(set);
            type_normal = Color.parseColor(road);
        }

        public int getType_satellite() {
            return type_satellite;
        }

        public int getType_normal() {
            return type_normal;
        }
    }

    public final LegColor usual       = new LegColor(0xAAFFFFFF,0xAA0F0F0F);
    public final LegColor highlighted = new LegColor(0xAAFF0000,0xAAFF0000);

    /** Corresponds to line 292 of original code */
/*  ///// Class seems unnecessary:
    private class ProjHelper { //mover here from the end of the file
        private GoogleMap googM;

        public ProjHelper(GoogleMap gm){
            googM = gm;
        }

        public Projection getProjection

        public void draw(){
            // dummy function (?!)
        }

    }*/

    private List<Marker> markersInCluster;
    private List<Marker> displayedMarkers;
    private Marker lastSpiderfiedCluster;
//    private List<GoogleMap.OnMarkerClickListener> markerListenerRefs; // TODO: Remove when certain
//    private List<spiderListener> spiderListenerRefs; // TODO: Remove when certain

    private boolean spiderfying = false;
    private boolean unspiderfying = false;
    private boolean firstonly = true;
    private float zoomLevelOnLastSpiderfy;

    private HashMap<Marker,Boolean> omsDataAvailability = new HashMap<Marker, Boolean>();
    private HashMap<Marker,_omsData> omsData= new HashMap<Marker, _omsData>();
    private HashMap<Marker,Boolean> spiderfyable = new HashMap<Marker, Boolean>();

    ////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////  METHODS BEGIN HERE  /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////

    public OverlappingMarkerSpiderfier(GoogleMap gm, Object ... varArgs) throws IllegalArgumentException { /** Corresponds to line 53 of original code*/
        this.gm=gm;
        mt = gm.getMapType();
//      ProjHelper projHelper = new ProjHelper(gm);
        if (varArgs.length > 0)
            assignVarArgs(varArgs);
        initMarkerArrays();

        // Listeners:
        gm.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                if(lastSpiderfiedCluster !=null && cameraPosition.zoom != zoomLevelOnLastSpiderfy){
                    unspiderfy(lastSpiderfiedCluster);
                    lastSpiderfiedCluster =null;
                }
            }
        });
    }

    //Right now method only checks the structure + names
    private boolean assignVarArgs(Object[] varArgs){
        int varLen=varArgs.length;
        if(varLen % 2 != 0){
            throw new IllegalArgumentException("Number of args is uneven.");
        }
        for(int ind=0; ind<varLen; ind=+2){
            String key = (String) varArgs[ind];
                 if(key.equals(ARG_KEEP_SPIDERFIED)){}
            else if(key.equals(ARG_MARK_WONT_HIDE)){}
            else if(key.equals(ARG_MARK_WONT_MOVE)){}
            else if(key.equals(ARG_NEARBY_DISTANCE)){}
            else if(key.equals(ARG_CS_SWITCHOVER)){}
            else if(key.equals(ARG_LEG_WEIGHT)){}
            else throw new IllegalArgumentException("Invalid argument name.");
        }
        return true;
    }

    private void initMarkerArrays(){ /** Corresponds to line 61 of original code*/
        markersInCluster = new ArrayList<Marker>();
        displayedMarkers = new ArrayList<Marker>();
//        markerListenerRefs = new ArrayList<GoogleMap.OnMarkerClickListener>(); // TODO: Remove when certain
//        spiderListenerRefs = new ArrayList<spiderListener>(); // TODO: Remove when certain;

    }

    private Marker addMarker(Marker marker){ /** Corresponds to line 65 of original code*/
        if(!isSpiderfyalbe(marker)){ // Added this function to the library source directly!
//          markerListenerRefs = [ge.addListener(marker, 'click', (event) => @spiderListener(marker, event))]
            if (!markersWontHide){
            //markerListenerRefs.push(ge.addListener(marker, 'visible_changed', => @markerChangeListener(marker, no)))
                markerChangeListener(marker,"visible_changed",false);
            }

            if (!markersWontMove){
            //markerListenerRefs.push(ge.addListener(marker, 'position_changed', => @markerChangeListener(marker, yes)))
                markerChangeListener(marker,"position_changed",false);
            }

//            markerListenerRefs.add(null); // TODO: Remove when certain
            markersInCluster.add(marker);

            setSpiderfyalbe(marker, true);
        }
        return marker;
    }

    private void markerChangeListener(Marker marker,String action, boolean positionChanged){ /** Corresponds to line 77 of original code*/
        if (hasOmsData(marker) && (positionChanged || !marker.isVisible()) && !(spiderfying || unspiderfying) ){
            unspiderfy(positionChanged ? marker : null);
        }
    }

    private List<Marker> getMarkersInCluster(){ /** Corresponds to line 81 of original code*/
        //  Meant to return a COPY of the original markersInCluster' list; Currently, this is a SHALLOW copy and might cause problems.
        List<Marker> markersCopy = new ArrayList<Marker>(markersInCluster);
        return markersCopy;
    }

    private void removeMarker(Marker marker){ /** Corresponds to line 83 of original code*/
        if (hasOmsData(marker))
            unspiderfy(marker);

        int i = markersInCluster.indexOf(marker);
        if (i>=0){
            // Remove listeners
                //TODO?
            setOmsDataAvailable(marker, false);
            markersInCluster.remove(i);
        }
    }

    private void clearMarkers(){ /** Corresponds to line 93 of original code*/
        for (Marker marker : markersInCluster) {
            // Remove listeners (methinks this is redundant in java due to "garbage collection")
            unspiderfy(marker);
            setOmsDataAvailable(marker, false);
        }
        initMarkerArrays();
    }

    /**
     * Lines 102-117 in original code seems irrelevant in java
     */
    // available listeners: click(marker), spiderfy(markersInCluster), unspiderfy(markersInCluster)

    private List<Point> generatePtsCircle (int count, Point centerPt){ /** Corresponds to line 119 of original code*/
        int circumference = circleFootSeparation * ( 2 + count);
        double legLength = circumference / TWO_PI * RADIUS_SCALE_FACTOR; // = radius from circumference
        double angleStep = TWO_PI / count;
        double angle;
        List<Point> points = new ArrayList<Point>(count);
        for (int ind = 0; ind < count; ind++) {
            angle = circleStartAngle + ind * angleStep;
            points.add(new Point((int)(centerPt.x + legLength * Math.cos(angle)),(int)(centerPt.y + legLength * Math.sin(angle))));
        }
        return points;
    }

    private List<Point> generatePtsSpiral (int count, Point centerPt){ /** Corresponds to line 128 of original code*/
        double legLength = spiralLengthStart * RADIUS_SCALE_FACTOR;
        double angle = 0;
        List<Point> points = new ArrayList<Point>(count);
        for (int ind = 0; ind < count; ind++) {
            angle += spiralFootSeparation / legLength + ind * 0.0005;
            points.add(new Point((int)(centerPt.x + legLength * Math.cos(angle)),(int)(centerPt.y + legLength * Math.sin(angle))));
            legLength += TWO_PI * spiralLengthFactor / angle;
        }
        return points;
    }

    public void spiderListener(Marker cluster){ /** Corresponds to line 138 of original code*/
    //TODO this should probably replace the onClick listener altogether
        boolean clusterSpiderfied = hasOmsData(cluster);
        if (clusterSpiderfied && !keepSpiderfied){
            Log.d(LOGTAG, "unspiderfy called from spiderListener");
//            unspiderfy(cluster);
        }
        else {
            List<MarkerData> nearbyMarkerData = new ArrayList<MarkerData>();
            List<Marker> nonNearbyMarkers = new ArrayList<Marker>();
            int nDist = nearbyDistance;
            int pxSq = nDist * nDist;
            Point mPt, markerPt = llToPt(cluster.getPosition());
            markersInCluster = cluster.getMarkers();
            displayedMarkers = gm.getDisplayedMarkers(); //could be very slow
            List<Marker> markersToConsider = new ArrayList<Marker>();
            markersToConsider.addAll(displayedMarkers); markersToConsider.addAll(markersInCluster);
            LatLngBounds llb = gm.getProjection().getVisibleRegion().latLngBounds;
            for (Marker markers_item : markersToConsider) {
                if(!llb.contains(markers_item.getPosition()) || markers_item == cluster)
                    continue;
                mPt = proj.toScreenLocation(markers_item.getPosition());
                if (ptDistanceSq(mPt,markerPt) < pxSq)
                    nearbyMarkerData.add(new MarkerData(markers_item,mPt));
                else
                    nonNearbyMarkers.add(markers_item);
            }

            if (nearbyMarkerData.size() == 1) {// 1 => only the one clicked => none nearby
                //trigger onMarkerClick event TODO Probably missing something here too. same as above
            } else {
                spiderfy(nearbyMarkerData,nonNearbyMarkers);
                zoomLevelOnLastSpiderfy = gm.getCameraPosition().zoom;
                lastSpiderfiedCluster = cluster;
            }
        }
    }

    private List<Marker> markersNearMarker(Marker marker) { /** Corresponds to line 161 of original code*/
        try {waitForMapIdle();} catch (InterruptedException e){}

        int nDist = nearbyDistance;
        int pxSq = nDist * nDist;
        Point markerPt = proj.toScreenLocation(marker.getPosition()); //using android maps api instead of llToPt and ptToLl
        Point mPt;
        List<Marker> markersNearMarker = new ArrayList<Marker>();
        for (Marker markers_item : markersInCluster) {
            if(!markers_item.isVisible() /* || markers_item instanceof Marker || (markers_item.map != null) */){ //no idea whether check for the rest of the conditions
                continue;
            }
            mPt = proj.toScreenLocation(hasOmsData(marker) ? omsData.get(markers_item).usualPosition : markers_item.getPosition());
            if (ptDistanceSq(mPt,markerPt) < pxSq){
                markersNearMarker.add(markers_item);
                if (firstonly){
                    firstonly = false;
                    break;
                }
            }
        }
        return markersNearMarker;
    }

    /**
     * Corresponds to line 176 of original code
     *
     * Returns an array of all markersInCluster that are near one or more other markersInCluster — i.e. those will be spiderfied when clicked.
     * This method is several orders of magnitude faster than looping over all markersInCluster calling markersNearMarker
     * (primarily because it only does the expensive business of converting lat/lons to pixel coordinates once per marker).
     *
     * @param marker
     * @return
     */
    private List<Marker> markersNearAnyOtherMarker(Marker marker){ //176
        try {waitForMapIdle();} catch (InterruptedException e){}

        int nDist = nearbyDistance;
        int pxSq = nDist * nDist;
        int numMarkers = markersInCluster.size();
        List<MarkerData> nearbyMarkerData = new ArrayList<MarkerData>(numMarkers);
        Point pt;

        for (Marker markers_item : markersInCluster) {
            pt = proj.toScreenLocation(hasOmsData(marker) ? omsData.get(markers_item).usualPosition : markers_item.getPosition());
            nearbyMarkerData.add(new MarkerData(markers_item,pt,false));
        }

        Marker m1, m2;
        MarkerData m1Data, m2Data;
        for (int i1=0; i1 < numMarkers; i1++) {
            m1 = markersInCluster.get(i1);
            if(!m1.isVisible() /* || (markers_item.map != null) */){
                continue;
            }
            m1Data = nearbyMarkerData.get(i1);
            if(m1Data.willSpiderfy)
                continue;
            for(int i2=0; i2 < numMarkers; i2++){
                m2 = markersInCluster.get(i2);
                if(i2==i1 || !m2.isVisible())
                    continue;
                m2Data = nearbyMarkerData.get(i2);
                if(i2 < i1 && !m2Data.willSpiderfy)
                    continue;
                if(ptDistanceSq(m1Data.markerPt,m2Data.markerPt) < pxSq){
                    m1Data.willSpiderfy=true;
                    m2Data.willSpiderfy=true;
                    break;
                }
            }
        }
        ArrayList<Marker> toSpiderfy = new ArrayList<Marker>(numMarkers);
        for (int i=0; i < numMarkers; i++) {
            if (nearbyMarkerData.get(i).willSpiderfy)
                toSpiderfy.add(nearbyMarkerData.get(i).marker);
        }
        return toSpiderfy;
    }

    private Marker highlight (Marker marker){ /** Corresponds to line 198 of original code*/
        _omsData data = omsData.get(marker);
        switch (gm.getMapType()){
            case GoogleMap.MAP_TYPE_NORMAL:
                data.leg.setColor(highlighted.getType_normal());
                data.leg.setZIndex(highlightedLegZIndex);
                break;
            case GoogleMap.MAP_TYPE_SATELLITE:
                data.leg.setColor(highlighted.getType_satellite());
                data.leg.setZIndex(highlightedLegZIndex);
                break;
            default:
                throw new IllegalArgumentException("Passed array is empty.");
        }
        return marker;
    }

    private void unhighlight (Marker marker){ /** Corresponds to line 202 of original code*/
        _omsData data = omsData.get(marker);
        switch (gm.getMapType()){
            case GoogleMap.MAP_TYPE_NORMAL:
                data.leg.setColor(usual.getType_normal());
                data.leg.setZIndex(usualLegZIndex);
                break;
            case GoogleMap.MAP_TYPE_SATELLITE:
                data.leg.setColor(usual.getType_satellite());
                data.leg.setZIndex(usualLegZIndex);
                break;
            default:
                throw new IllegalArgumentException("Passed array is empty.");
        }
    }

    private void spiderfy(List<MarkerData> clusteredMarkersData,List<Marker> nearbyMarkers){ /** Corresponds to line 207 of original code*/
        // renamed from original as follows: nearbyMarkersData => clusteredMarkersData; nonNearbyMarkers => nearbyMarkers
        if (clusteredMarkersData.size() == 0 || markersInCluster.size() == 0)
            return; //cant' work with empty arrays...
        spiderfying = true;
        int numFeet = clusteredMarkersData.size();
        List<Point> nearbyMarkerPts = new ArrayList<Point>(numFeet);
        for (MarkerData markerData : clusteredMarkersData) {
            nearbyMarkerPts.add(markerData.markerPt);
        }
        Point bodyPt = ptAverage(nearbyMarkerPts);
        List<Point> footPts;
        if (numFeet >= circleSpiralSwitchover){
            footPts=generatePtsSpiral(numFeet,bodyPt);
            Collections.reverse(footPts);
        }
        else
            footPts=generatePtsCircle(numFeet,bodyPt);

        List<Marker> spiderfiedMarkers = new ArrayList<Marker>();
        for (int ind =0; ind < numFeet; ind++){
            Point footPt = footPts.get(ind);
            LatLng footLl = ptToLl(footPt);
/*
            List<Integer> distances = new ArrayList<Integer>(numFeet);
            for (Point nearbyMarkerPt : nearbyMarkerPts) {
                distances.add(ptDistanceSq(nearbyMarkerPt,footPt));
            }*/
            MarkerData nearestMarkerData = clusteredMarkersData.get(ind);
            Marker clusterNearestMarker = nearestMarkerData.marker;
            Polyline leg = gm.addPolyline(new PolylineOptions()
                    .add(clusterNearestMarker.getPosition(), footLl)
                    .color(usual.getType_normal())
                    .width(legWeight)
                    .zIndex(usualLegZIndex));
            omsData.put(clusterNearestMarker,new _omsData()
                    .leg(leg)
                    .usualPosition(clusterNearestMarker.getPosition()));
            // lines 228-233 in original code seem irrelevant in java
//          clusterNearestMarker.setClusterGroup(ClusterGroup.NOT_CLUSTERED);
            clusterNearestMarker.setPosition(footLl);
            // set clusterNearestMarker zIndex is unavailable in android :\

            omsDataAvailability.put(clusterNearestMarker, true); //same as @spiderfied = yes
            spiderfiedMarkers.add(clusterNearestMarker);
        }
        spiderfying=false;
        //  trigger("spiderfy",spiderfiedMarkers,nearbyMarkers); // ?! Todo: find an idea what to do with this.

    }

    /**
     * Corresponds to line 241 of original code
     *
     * Returns any spiderfied markers to their original positions, and triggers any listeners you may have set for this event.
     * Unless no markersInCluster are spiderfied, in which case it does nothing.
     */
    private Marker unspiderfy(Marker clusterToUnspiderfy){ //241
        // this function has to return everything to its original state
        if (clusterToUnspiderfy!=null){ //Todo: make sure that this "if" is needed at all
            unspiderfying=true;
            List<Marker> unspiderfiedMarkers = new ArrayList<Marker>(), nonNearbyMarkers = new ArrayList<Marker>();
            for (Marker marker : markersInCluster) {
                if(hasOmsData(marker)){// ignoring the possibility that (params.markerNotToMove != null)
                    marker.setPosition(omsData.get(marker).leg(null).getUsualPosition());
                    //skipped lines 250-254 from original code
                    setOmsDataAvailable(marker, false); //260
                    unspiderfiedMarkers.add(marker);
                } else
                    nonNearbyMarkers.add(marker);
            }
            unspiderfying=false;
        }
        return clusterToUnspiderfy; // return self, for chaining
    }

    private int ptDistanceSq(Point pt1, Point pt2){ /** Corresponds to line 264 of original code*/
        int dx = pt1.x - pt2.x;
        int dy = pt1.y - pt2.y;
        return (dx * dx + dy * dy);
    }

    private Point ptAverage(List<Point> pts){ /** Corresponds to line 269 of original code*/
        int sumX=0, sumY=0, numPts=pts.size();
        for (Point pt : pts) {
            sumX += pt.x;
            sumY += pt.y;
        }
        return new Point(sumX / numPts,sumY / numPts);
    }


    private Point llToPt(LatLng ll) { /** Corresponds to line 276 of original code*/
        proj = gm.getProjection();
        return proj.toScreenLocation(ll);   // the android maps api equivalent
    }

    private LatLng ptToLl(Point pt){ /** Corresponds to line 277 of original code*/
        proj = gm.getProjection();
        return proj.fromScreenLocation(pt); // the android maps api equivalent
    }

    private int minDistExtract(List<Integer> distances){ /** Corresponds to line 279 of original code */
        if(distances.size()==0)
            throw new IllegalArgumentException("Passed array is empty.");

        int bestVal = distances.get(0);
        int bestValInd = 0;

        for (int ind=1; ind < distances.size(); ind++) {
            if(distances.get(ind) < bestVal){
                bestValInd=ind;
            }
        }
        distances.remove(bestValInd);
        return bestValInd;
    }

    /* arrIndexOf is irrelevant in java -  Corresponds to line 287 of original code */

    /** ////////// BELOW ARE HELPER FUNCTION NOT FOUND IN THE ORIGINAL CODE /////////// */

    private void waitForMapIdle() throws InterruptedException{
        while (proj==null){  // check for "idle" event on map (i.e. no animation is playing)
            Thread.sleep(50);// "Must wait for 'idle' event on map before calling whatever's next"
        }
    }

    private void setSpiderfyalbe(Marker marker, boolean mode){
        spiderfyable.put(marker,mode);
    }

    private boolean isSpiderfyalbe(Marker marker){
        return spiderfyable.containsKey(marker) ? spiderfyable.get(marker) : false;
    }

    private void setOmsDataAvailable(Marker marker, boolean isAvailable){
        omsDataAvailability.put(marker,isAvailable);
    }

    private boolean hasOmsData(Marker marker){
        return omsDataAvailability.containsKey(marker) ? omsDataAvailability.get(marker) : false;
    }

    public boolean isAnythingSpiderfied() {
        return lastSpiderfiedCluster!=null;
    }
}