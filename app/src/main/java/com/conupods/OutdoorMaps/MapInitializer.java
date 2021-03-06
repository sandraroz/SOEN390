package com.conupods.OutdoorMaps;

import android.Manifest;
import android.content.Context;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import android.graphics.drawable.AnimatedStateListDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;

import com.conupods.IndoorMaps.ConcreteBuildings.HBuilding;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.conupods.App;
import com.conupods.Calendar.CalendarObject;
import com.conupods.Calendar.Event;
import com.conupods.IndoorMaps.IndoorBuildingOverlays;
import com.conupods.IndoorMaps.IndoorOverlayHandlers.CCBuildingHandler;
import com.conupods.IndoorMaps.IndoorOverlayHandlers.DefaultHandler;
import com.conupods.IndoorMaps.IndoorOverlayHandlers.HallBuildingHandler;
import com.conupods.IndoorMaps.IndoorOverlayHandlers.IndoorOverlayHandler;
import com.conupods.IndoorMaps.IndoorOverlayHandlers.MBBuildingHandler;
import com.conupods.IndoorMaps.IndoorOverlayHandlers.VLBuildingHandler;
import com.conupods.MapsActivity;
import com.conupods.OutdoorMaps.Remote.IGoogleAPIService;
import com.conupods.OutdoorMaps.Services.PlacesService;
import com.conupods.OutdoorMaps.View.PointsOfInterest.SliderAdapter;
import com.conupods.OutdoorMaps.View.Settings.SettingsActivity;
import com.conupods.OutdoorMaps.View.Settings.SettingsPersonalActivity;
import com.conupods.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.ViewPager;

public class MapInitializer {

    public static final double ZOOM_LEVEL = 18.3;

    private CameraController mCameraController;
    private GoogleMap mMap;
    private BuildingInfoWindow mBuildingInfoWindow;
    private IndoorBuildingOverlays mIndoorBuildingOverlays;
    private OutdoorBuildingOverlays mOutdoorBuildingOverlays;
    private IndoorOverlayHandler mIndoorOverlayHandler;
    private SearchView mSearchBar;
    private Button mSgwButton, mLoyButton;
    IGoogleAPIService mService;
    MapsActivity mView;
    SliderAdapter mSliderAdapter;
    ViewPager mViewPager;

    List<Button> mButtonsH = new ArrayList<>();
    List<Button> mButtonsMB = new ArrayList<>();
    List<Button> mButtonsVL = new ArrayList<>();
    private static final String TAG = "MapInitializer";

    public MapInitializer(CameraController cameraController, IndoorBuildingOverlays indoorBuildingOverlays,
                          OutdoorBuildingOverlays outdoorBuildingOverlays, GoogleMap map,
                          BuildingInfoWindow buildingInfoWindow, Button sgwButton, Button loyButton,
                          IGoogleAPIService googleAPIService, MapsActivity view,
                          SliderAdapter sliderAdapter) {

        mCameraController = cameraController;
        mIndoorBuildingOverlays = indoorBuildingOverlays;
        mBuildingInfoWindow = buildingInfoWindow;
        mOutdoorBuildingOverlays = outdoorBuildingOverlays;
        mMap = map;
        HBuilding.getInstance();


        mView = view;
        mService = googleAPIService;
        mSliderAdapter = sliderAdapter;

        mSgwButton = sgwButton;
        mLoyButton = loyButton;


        IndoorOverlayHandler mbBuildingHandler = new MBBuildingHandler();
        IndoorOverlayHandler vlBuildingHandler = new VLBuildingHandler();
        IndoorOverlayHandler ccBuildingHandler = new CCBuildingHandler();
        IndoorOverlayHandler mDefaultHandler = new DefaultHandler();

        this.mIndoorOverlayHandler = new HallBuildingHandler();
        mIndoorOverlayHandler.setNextInChain(mbBuildingHandler);
        mbBuildingHandler.setNextInChain(vlBuildingHandler);
        vlBuildingHandler.setNextInChain(ccBuildingHandler);
        ccBuildingHandler.setNextInChain(mDefaultHandler);
    }

    /**
     * Every time the camera is idle, check if the zoom level is above 18.3
     * If that is the case, remove outdoor overlays, and then handle indoor building overlay requests
     * Otherwise, remove all indoor overlays objects and display outdoor overlays
     */
    public void onCameraChange() {

        mMap.setOnCameraIdleListener(() -> {

            LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

            if (mMap.getCameraPosition().zoom > ZOOM_LEVEL) {
                mOutdoorBuildingOverlays.removePolygons();
                mIndoorOverlayHandler.checkBounds(bounds, mIndoorBuildingOverlays);
            } else {
                mIndoorBuildingOverlays.hideLevelButton();
                mIndoorBuildingOverlays.removeOverlay();
                mOutdoorBuildingOverlays.overlayPolygons();
            }
        });
    }

    private void changeButtonColors(List<Button> floorButtons) {
        for (Button button : floorButtons) {
            if (!button.isPressed()) {
                button.setBackgroundColor(Color.WHITE);
            }
            if (button.isPressed()) {
                button.setBackgroundColor(Color.LTGRAY);
            }
        }
    }

    /**
     * creates button objects
     */
    private void createButton(int index, IndoorBuildingOverlays.BuildingCodes building, List<Button> buttonContainer, View buttonId) {

        Button b = (Button) buttonId;
        b.setOnClickListener((View v) -> {
            mIndoorBuildingOverlays.changeOverlay(index, building);
            changeButtonColors(buttonContainer);
        });
        buttonContainer.add(b);
    }

    /**
     * Listener for floor buttons, display appropriate floor blueprint
     *
     * @param floorButtons
     */
    public void initializeFloorButtons(View floorButtons) {

        createButton(7, IndoorBuildingOverlays.BuildingCodes.VL, mButtonsVL, floorButtons.findViewById(R.id.loy_vl1));
        createButton(8, IndoorBuildingOverlays.BuildingCodes.VL, mButtonsVL, floorButtons.findViewById(R.id.loy_vl2));
        createButton(4, IndoorBuildingOverlays.BuildingCodes.MB, mButtonsMB, floorButtons.findViewById(R.id.MB1));
        createButton(5, IndoorBuildingOverlays.BuildingCodes.MB, mButtonsMB, floorButtons.findViewById(R.id.MBS2));
        createButton(0, IndoorBuildingOverlays.BuildingCodes.H, mButtonsH, floorButtons.findViewById(R.id.hall1));
        createButton(1, IndoorBuildingOverlays.BuildingCodes.H, mButtonsH, floorButtons.findViewById(R.id.hall2));
        createButton(2, IndoorBuildingOverlays.BuildingCodes.H, mButtonsH, floorButtons.findViewById(R.id.hall8));
        createButton(3, IndoorBuildingOverlays.BuildingCodes.H, mButtonsH, floorButtons.findViewById(R.id.hall9));
    }

    // The two campus swap buttons
    public void initializeToggleButtons() {

        mSgwButton.setBackgroundColor(Color.WHITE);
        mSgwButton.setTextColor(Color.BLACK);
        mLoyButton.setBackgroundColor(Color.WHITE);
        mLoyButton.setTextColor(Color.BLACK);

        mSgwButton.setOnClickListener((View v) -> {
            PlacesService mPlaceService = new PlacesService(mView, mService, mMap);
            mPlaceService.getAllPointsOfInterest("SGW");
            mCameraController.moveToLocationAndAddMarker(CameraController.SGW_CAMPUS_LOC);

            mSgwButton.setBackgroundResource(R.drawable.conu_gradient);
            mSgwButton.setTextColor(Color.WHITE);
            mLoyButton.setBackgroundColor(Color.WHITE);
            mLoyButton.setTextColor(Color.BLACK);

            // restore initial camera zoom level
            mMap.animateCamera(CameraUpdateFactory.zoomTo(16.0f));
        });

        mLoyButton.setOnClickListener((View v) -> {
            PlacesService mPlaceService = new PlacesService(mView, mService, mMap);
            mPlaceService.getAllPointsOfInterest("LOY");
            mCameraController.moveToLocationAndAddMarker(CameraController.LOY_CAMPUS_LOC);

            mLoyButton.setBackgroundResource(R.drawable.conu_gradient);
            mLoyButton.setTextColor(Color.WHITE);
            mSgwButton.setBackgroundColor(Color.WHITE);
            mSgwButton.setTextColor(Color.BLACK);

            // restore initial camera zoom level 
            mMap.animateCamera(CameraUpdateFactory.zoomTo(16.0f));
        });
    }

    public void initializeLocationButton(Button locationButton) {
        PlacesService mPlaceService = new PlacesService(mView, mService, mMap);
        mPlaceService.getAllPointsOfInterest("Current Location");
        locationButton.setOnClickListener((View view) -> {
            mPlaceService.getAllPointsOfInterest("Current Location");
            mCameraController.goToDeviceCurrentLocation();
            mSgwButton.setBackgroundColor(Color.WHITE);
            mSgwButton.setTextColor(Color.BLACK);
            mLoyButton.setBackgroundColor(Color.WHITE);
            mLoyButton.setTextColor(Color.BLACK);
        });
    }

    public SearchView initializeSearchBar(SearchView searchBar) {
        searchBar.setQueryHint("Where To?");
        searchBar.setTransitionName("BeginTransition");

        return searchBar;
    }

    public void initializeBuildingMarkers() {
        mBuildingInfoWindow.generateBuildingMakers(mMap);
        mMap.setOnMarkerClickListener((Marker marker) -> {
            mMap.setInfoWindowAdapter(mBuildingInfoWindow);
            return false;
        });
    }

    public void launchSettingsActivity(MapsActivity current) {
        current.findViewById(R.id.settingsButton).setOnClickListener(view -> current.startActivityIfNeeded(new Intent(current, SettingsActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0));
    }


}
