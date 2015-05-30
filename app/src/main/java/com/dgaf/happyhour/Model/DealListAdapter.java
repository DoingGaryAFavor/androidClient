package com.dgaf.happyhour.Model;
import com.dgaf.happyhour.Controller.LocationService;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dgaf.happyhour.DealListType;
import com.dgaf.happyhour.R;
import com.dgaf.happyhour.View.RestaurantFragment;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.parse.DeleteCallback;
import com.parse.FindCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseGeoPoint;
import com.parse.ParseImageView;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by trentonrobison on 4/28/15.
 */
public class DealListAdapter extends RecyclerView.Adapter<DealListAdapter.ViewHolder> implements SwipeRefreshLayout.OnRefreshListener {
    private FragmentActivity activity;
    private ImageLoader imageLoader;
    private List<DealModel> dealItems;
    private ParseGeoPoint parseLocation;
    private double searchRadius;
    private DealListType listType;
    private LocationService userLocation;
    private SwipeRefreshLayout swipeRefresh;
    private static final String DEAL_LIST_CACHE = "dealList";
    private static Boolean[] loadedDeals = new Boolean[DealListType.values().length];


    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView deal;
        public TextView description;
        public TextView distance;
        public TextView restaurant;
        public TextView likes;
        public TextView hours;
        public ParseImageView thumbnail;
        public FragmentActivity activity;
        public String restaurantId;

        public ViewHolder(View itemView,FragmentActivity activity) {
            super(itemView);
            itemView.setOnClickListener(this);
            this.activity = activity;
            deal = (TextView) itemView.findViewById(R.id.deal);
            description = (TextView) itemView.findViewById(R.id.description);
            distance = (TextView) itemView.findViewById(R.id.distance);
            restaurant = (TextView) itemView.findViewById(R.id.restaurant);
            likes = (TextView) itemView.findViewById(R.id.likes);
            hours = (TextView) itemView.findViewById(R.id.hours);
            thumbnail = (ParseImageView) itemView.findViewById(R.id.thumb_nal);
            thumbnail.setPlaceholder(ContextCompat.getDrawable(itemView.getContext(), R.drawable.llama));
        }
        @Override
        public void onClick(View v) {
            Fragment restaurant = RestaurantFragment.newInstance(restaurantId);
            FragmentManager fragmentManager = activity.getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.main_fragment, restaurant).addToBackStack(null).commit();
        }

    }

    public DealListAdapter(FragmentActivity activity, SwipeRefreshLayout swipeRefresh, DealListType dealListType, double radiusMi) {
        this.activity = activity;
        this.imageLoader = ImageLoader.getInstance();
        this.swipeRefresh = swipeRefresh;
        this.dealItems = new ArrayList<>();

        swipeRefresh.setOnRefreshListener(this);
        listType = dealListType;
        loadedDeals[dealListType.ordinal()] = false;
        parseLocation = getLocation();
        loadLocalDeals(parseLocation, radiusMi);

    }

    public ParseGeoPoint getLocation() {
        // Geisel Library - Default Location
        double latitude = 32.881122;
        double longitude = -117.237631;
        if (!Build.FINGERPRINT.startsWith("generic")) {
            userLocation = new LocationService(activity);
            // Is user location available and are we not running in an emulator
            if (userLocation.canGetLocation()) {
                latitude = userLocation.getLatitude();
                longitude = userLocation.getLongitude();
            } else {
                userLocation.showSettingsAlert();
            }
        }
        return new ParseGeoPoint(latitude,longitude);
    }

    public void loadLocalDeals(ParseGeoPoint location, double radiusMi) {
        if (!location.equals(parseLocation)) {
            loadedDeals[listType.ordinal()] = false;
            parseLocation = location;
        }
        if (radiusMi != searchRadius) {
            loadedDeals[listType.ordinal()] = false;
            searchRadius = radiusMi;
        }
        // Setup the database Query
        ParseQuery<RestaurantModel> localRestaurants = ParseQuery.getQuery(RestaurantModel.class);
        localRestaurants.whereWithinMiles("location", location, radiusMi);
        localRestaurants.whereNear("location", location);
        ParseQuery<DealModel> localDeals = ParseQuery.getQuery(DealModel.class);
        localDeals.whereMatchesQuery("restaurantId", localRestaurants);
        switch(listType) {
            case DRINK:
                localDeals.whereEqualTo("tags","drink");
                break;
            case FOOD:
                localDeals.whereEqualTo("tags","food");
                break;
            case FEATURED:
                localDeals.whereEqualTo("tags","featured");
                break;
        }
        if (loadedDeals[listType.ordinal()]) {
            localDeals.fromLocalDatastore();
        }
        localDeals.include("restaurantId");
        Log.v("Parse info", "Deal list query started" );
        final DealListAdapter listAdapter = this;
        localDeals.findInBackground(new FindCallback<DealModel>() {
            public void done(List<DealModel> deals, ParseException e) {
                Log.v("Parse info","Deal list query returned");
                if (e == null) {
                    dealItems = deals;
                    loadedDeals[listType.ordinal()] = true;
                    // Release any objects previously pinned for this query.
                    ParseObject.unpinAllInBackground(DEAL_LIST_CACHE, dealItems, new DeleteCallback() {
                        public void done(ParseException e) {
                            if (e != null) {
                                Log.e("Parse error: ", e.getMessage());
                                return;
                            }
                            // Update refresh indicator
                            swipeRefresh.setRefreshing(false);
                            // Add the latest results for this query to the cache.
                            ParseObject.pinAllInBackground(DEAL_LIST_CACHE, dealItems);
                        }
                    });
                    listAdapter.notifyDataSetChanged();
                } else {
                    Log.e("Parse error: ", e.getMessage());
                }
            }
        });
    }

    @Override
    public void onRefresh() {
        parseLocation = getLocation();
        loadedDeals[listType.ordinal()] = false;
        loadLocalDeals(parseLocation, searchRadius);
    }

    @Override
    public DealListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.deal_list_item, parent, false);
        ViewHolder vh = new ViewHolder(v,activity);
        return vh;
    }

    @Override
    public int getItemCount() {
        return dealItems.size();
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        DealModel dealModel = dealItems.get(position);

        ParseFile imageFile = dealModel.getThumbnailFile();
        if (imageFile != null) {
            imageLoader.displayImage(imageFile.getUrl(), holder.thumbnail);
        }

        holder.deal.setText(dealModel.getTitle());
        holder.likes.setText("Rating: " + String.valueOf(dealModel.getUpVotes()));
        holder.description.setText(dealModel.getDescription());
        holder.restaurant.setText(dealModel.getRestaurant());
        holder.distance.setText(String.format("%.1f", dealModel.getDistanceFrom(parseLocation)) + " mi");
        holder.hours.setText("");
        holder.restaurantId = dealModel.getRestaurantId();
    }
}
