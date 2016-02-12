/*
 *  Copyright (C) 2016 Dev-iL
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License
 */
package dev.il.spiderficationsample;

import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.androidmapsextensions.ClusterGroup;
import com.androidmapsextensions.ClusterOptions;
import com.androidmapsextensions.ClusterOptionsProvider;
import com.androidmapsextensions.ClusteringSettings;
import com.androidmapsextensions.GoogleMap;
import com.androidmapsextensions.Marker;
import com.androidmapsextensions.MarkerOptions;
import com.androidmapsextensions.OnMapReadyCallback;
import com.androidmapsextensions.SupportMapFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;
import java.util.Random;

public class DemoMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private OverlappingMarkerSpiderfier oms;
    private ClusteringSettings clusterSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getExtendedMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add several markers in Israel.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        updateClusteringRadius(); // <= Assuming clustering is activated
        oms = new OverlappingMarkerSpiderfier(mMap);
        // Add several markers and move the camera
        LatLng ariel = new LatLng(32.1048861, 35.1753109);
        addDemoMarkersAround(mMap,ariel);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ariel,12.5f));

        // Set onClick listener configured for spiderfication:
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick (Marker marker){
                // We need to figure out if it was a seperate marker or a cluster marker
                if (marker.isCluster()) {
                    if (mMap.getCameraPosition().zoom >= 15) //Play around with this. We assume the SPIDERFICATION_ZOOM_THRSH is constant and never changes.
                        oms.spiderListener(marker); // That's where the magic happens
                    else {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                marker.getPosition(),
                                mMap.getCameraPosition().zoom + dynamicZoomLevel()));
                        updateClusteringRadius();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void addDemoMarkersAround(GoogleMap map, LatLng center){
        MarkerOptions options = new MarkerOptions();
        Random r = new Random();
        for (int k = 0; k < 20; k++){
            map.addMarker(options
                    .title("Place " + k)
                    .position(new LatLng(center.latitude  + r.nextGaussian()*0.002,
                                         center.longitude + r.nextGaussian()*0.002))
                    .clusterGroup(ClusterGroup.FIRST_USER)
            );
        }
    }

    private float dynamicZoomLevel() {
        float currZoomLvl = mMap.getCameraPosition().zoom;
        final float minZoomStepAtZoom = 17.3F, minZoomStep = 1.8F;
        final float maxZoomStepAtZoom = 7F, maxZoomStep = 2.8F;

        if (currZoomLvl >= minZoomStepAtZoom)
            return minZoomStep;
        else if (currZoomLvl <= maxZoomStepAtZoom)
            return maxZoomStep;
        else
            // simple interpolation:
            return (currZoomLvl - maxZoomStepAtZoom)
                    * (maxZoomStep - minZoomStep)
                    / (maxZoomStepAtZoom - minZoomStepAtZoom) + maxZoomStep;
    }

    private int clusterRadiusCalculation() {
        final int minRad = 0, maxRad = 150;
        final float minRadZoom = 10F, maxRadZoom = 7.333F;

        if (mMap.getCameraPosition().zoom >= minRadZoom) {

            return minRad;

        } else if (mMap.getCameraPosition().zoom <= maxRadZoom)
            return maxRad;
        else
            // simple interpolation:
            return (int) (maxRad - (maxRadZoom - mMap.getCameraPosition().zoom) *
                         (maxRad - minRad) / (maxRadZoom - minRadZoom));
    }

    private void updateClusteringRadius() {
        if (clusterSettings == null) {
            clusterSettings = new ClusteringSettings();
            clusterSettings.addMarkersDynamically(true);
            clusterSettings.clusterSize(clusterRadiusCalculation());
            /** Based on pl.mg6.android.maps.extensions.demo.ClusterGroupsFragment */
            ClusterOptionsProvider provider = new ClusterOptionsProvider() {
                @Override public ClusterOptions getClusterOptions(List<Marker> markers) {
                    float hue;
                    switch (markers.get(0).getClusterGroup()) {
                        case ClusterGroup.FIRST_USER:
                            hue = BitmapDescriptorFactory.HUE_ORANGE;
                            break;
                        case ClusterGroup.DEFAULT: // The color of "spiderfied at least once" clusters
                            hue = BitmapDescriptorFactory.HUE_GREEN;
                            break;
                        default: // ClusterGroup.NOT_CLUSTERED:
                            hue = BitmapDescriptorFactory.HUE_ROSE;
                            break;
                    }
                    BitmapDescriptor defaultIcon = BitmapDescriptorFactory.defaultMarker(hue);
                    return new ClusterOptions().icon(defaultIcon);                }
            };
            mMap.setClustering(clusterSettings.clusterOptionsProvider(provider));
        } else if (mMap.getCameraPosition().zoom > 13F){

        } else {
            clusterSettings.clusterSize(clusterRadiusCalculation());
        }
    }
}
