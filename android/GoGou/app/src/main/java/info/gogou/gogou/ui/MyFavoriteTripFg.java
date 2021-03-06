package info.gogou.gogou.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.octo.android.robospice.JacksonSpringAndroidSpiceService;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import info.gogou.gogou.R;
import info.gogou.gogou.app.GogouApplication;
import info.gogou.gogou.model.Trip;
import info.gogou.gogou.model.TripFilter;
import info.gogou.gogou.model.TripList;
import info.gogou.gogou.request.TripRequest;
import info.gogou.gogou.constants.GoGouConstants;
import info.gogou.gogou.constants.GoGouIntents;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

public class MyFavoriteTripFg extends RoboFragment {

    private static final String TAG = "MyFavoriteTripFg";

    @InjectView(R.id.myFavorite_trip_list)
    private ListView trip_listview;

    private ArrayList<HashMap<String,String>> mTripList = new ArrayList<HashMap<String,String>>();

    private TripListAdapter mTripListAdapter;

    // cache the trip list for now
    private TripList mCachedList =  new TripList();

    private Activity mActivity;

    private ProgressDialog mProgressSpinner;

    private SpiceManager mSpiceManager = new SpiceManager(
            JacksonSpringAndroidSpiceService.class);

    private TripFilter mTripFilter = new TripFilter();

    private int mPageSize = 40; //number of items to load for one REST request
    private int mCurrentPage = 1; // current item index from where REST request loads items
    private int mSizeofCurPage = 0; // number of item in current page
    private boolean mIsLoading = false;

    private String mUserName = null;

    private Long tripId;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mProgressSpinner = new ProgressDialog(mActivity, R.style.MyTheme);
        mProgressSpinner.setIndeterminate(false);
        mProgressSpinner.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        View trip_view = inflater.inflate(R.layout.myfavorite_tripfg_layout, container, false);

        Bundle bundle = getArguments();
        mUserName = bundle.getString(GoGouConstants.KEY_USER_NAME);
        Log.d(TAG, "My favorite trip of user: " + mUserName);

        /**
         * 行程列表
         */
        trip_listview=(ListView)trip_view.findViewById(R.id.myFavorite_trip_list);
        mTripListAdapter= new TripListAdapter(mActivity, mTripList);
        trip_listview.setAdapter(mTripListAdapter);

        trip_listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (parent.getId()) {
                    case R.id.myFavorite_trip_list:
                        Intent intent = new Intent(mActivity, ScheduleDetailActivity.class);
                        Trip trip = mCachedList.get(position);
                        intent.putExtra(GoGouIntents.TRIP, trip);
                        intent.putExtra(GoGouIntents.UNIQUE_ID, TAG);
                        startActivity(intent);
                }
            }
        });

//        trip_listview.setOnScrollListener(new ManualScrollListener());

        return trip_view;
    }


    private void addTripItemToListInternal(Trip trip)
    {
        HashMap<String,String> tripItem=new HashMap<String,String>();
        tripItem.put("id",trip.getId().toString());
        tripItem.put(GoGouConstants.KEY_DEPART, trip.getOrigin());
        tripItem.put(GoGouConstants.KEY_DESTINATION, trip.getDestination());
        int spaceIndex = trip.getArrival().indexOf(" ");
        tripItem.put(GoGouConstants.KEY_ARRIVALDATE, trip.getArrival().substring(0, spaceIndex));
        spaceIndex = trip.getDeparture().indexOf(" ");
        tripItem.put(GoGouConstants.KEY_DEPARTDATE, trip.getDeparture().substring(0, spaceIndex));
        mTripList.add(tripItem);
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.mActivity = activity;
    }


    @Override
    public void onStart() {
        mSpiceManager.start(mActivity);
        super.onStart();
    }

    @Override
    public void onStop() {
        mSpiceManager.shouldStop();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mTripFilter.setCurrentPage(mCurrentPage);
        mTripFilter.setPageSize(mPageSize);
        mTripFilter.setDeparture("desc");
        mTripFilter.setRatingOrder("desc");
        mCachedList.clear();
        mTripList.clear();
        performRequest();
    }

    private void performRequest() {
        mProgressSpinner.show();
        TripRequest.GetTripListRequest request = new TripRequest.GetTripListRequest(mTripFilter);
        mSpiceManager.execute(request, new GetTripListRequestListener());
    }


    private class GetTripListRequestListener implements
            RequestListener<TripList> {

        @Override
        public void onRequestFailure(SpiceException e) {
            Toast.makeText(mActivity,
                    "Error during request: " + e.getLocalizedMessage(), Toast.LENGTH_LONG)
                    .show();
            mProgressSpinner.dismiss();
        }

        @Override
        public void onRequestSuccess(TripList tripList) {

            mProgressSpinner.dismiss();
            if (tripList == null)
            {
                Toast.makeText(mActivity,
                        "Getting trip list failed", Toast.LENGTH_LONG);
            }
            else
            {
                Log.d(TAG, "There are " + tripList.size() + " trips");

                List<Long> favorite_trip_list = read();

                if(favorite_trip_list != null){
                    favorite_trip_list = removeDuplicate(read());
                    Log.d(TAG, "favorite trip size "+ favorite_trip_list.size());
                    for(int i=0; i< favorite_trip_list.size(); i++){

                        Log.d(TAG, "trip list id favorite 2 = "+ favorite_trip_list.get(i));

                        for (Trip trip : tripList)
                        {
                            Log.d(TAG, "trip list id 1 = "+ trip.getId());
                            if(trip.getId().equals(favorite_trip_list.get(i))){
                                Log.d(TAG, "trip list id favorite 1 = "+ favorite_trip_list.get(i));
                                Log.d(TAG, "trip list id 2 = "+ trip.getId());
                                addTripItemToListInternal(trip);
                                mCachedList.add(trip);
                            }
                        }
                    }
                }

                Log.d(TAG,"mTripList size "+ mTripList.size());
                mTripListAdapter.notifyDataSetChanged();
                mIsLoading = false;
            }
        }
    }


    public static List<Long> removeDuplicate(List<Long> list)
    {
        Set set = new LinkedHashSet<Long>();
        set.addAll(list);
        list.clear();
        list.addAll(set);
        return list;
    }

    public List<Long> read() {
        try {
            FileInputStream inStream = getActivity().openFileInput("Trip.txt");
            byte[] buffer = new byte[1024];
            int hasRead = 0;
            StringBuilder sb = new StringBuilder();
            while ((hasRead = inStream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, hasRead));
            }

            String regex = ",|，|\\s+";
            String strAry[] = sb.toString().split(regex);
            List<Long> list = new ArrayList<Long>();

            if(strAry != null){

                Log.d(TAG, "favorite trip length " + strAry.length);

                for (int i = 0; i < strAry.length; i++) {
                    list.add(Long.parseLong(strAry[i]));
                    Log.d(TAG, "favorite trip id " + Long.parseLong(strAry[i]));
                }
            }

            inStream.close();
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private class ManualScrollListener implements AbsListView.OnScrollListener {

        private boolean performLoading = false;

        public ManualScrollListener() {
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                             int visibleItemCount, int totalItemCount) {

            Log.d(TAG, "First visible item index: " + firstVisibleItem);
            Log.d(TAG, "Number of visible cells: " + visibleItemCount);
            Log.d(TAG, "Number of items in the list adaptor: " + totalItemCount);

            // when the view reaches to the last item, a request is performed
            if ((firstVisibleItem + visibleItemCount) >= totalItemCount)
            {
                performLoading = true;
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            Log.d(TAG, "Scroll state is changed to : " + scrollState);
            if (scrollState ==  SCROLL_STATE_TOUCH_SCROLL && !mIsLoading && performLoading) {
                Log.d(TAG, "Perform a read loading request...");
                mTripFilter.setCurrentPage(mCurrentPage);
                mTripFilter.setPageSize(mPageSize);
                mTripFilter.setDeparture("desc");
                mTripFilter.setRatingOrder("desc");
                //mTripFilter.setUserName(mUserName);
                mCachedList.clear();
                mTripList.clear();
                performRequest();
                mIsLoading = true;
                performLoading = false;

            }
        }
    }

    private static class TripListAdapter extends BaseAdapter {
        private Activity mActivity;
        private ArrayList<HashMap<String, String>> mData;
        private static LayoutInflater mInflater=null;
        //public ImageLoader imageLoader;

        public TripListAdapter(Activity a, ArrayList<HashMap<String, String>> d) {
            mActivity = a;
            mData = d;
            mInflater = (LayoutInflater)mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //imageLoader=new ImageLoader(activity.getApplicationContext());
        }

        public int getCount() {
            return mData.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View vi=convertView;
            if(convertView==null)
                vi = mInflater.inflate(R.layout.schedule_list_row, null);

            TextView departureTV = (TextView)vi.findViewById(R.id.departureTV); // departure
            TextView destinationTV = (TextView)vi.findViewById(R.id.destinationTV); // destination
            TextView arrivalDateTV = (TextView)vi.findViewById(R.id.arrivalDateTV); // arrival Date
            TextView deparDateTV = (TextView)vi.findViewById(R.id.departDateTV); // depart Date

            HashMap<String, String> items = new HashMap<String, String>();
            items = mData.get(position);

            // Setting all values in listview
            departureTV.setText(items.get(GoGouConstants.KEY_DEPART));
            destinationTV.setText(items.get(GoGouConstants.KEY_DESTINATION));
            arrivalDateTV.setText(items.get(GoGouConstants.KEY_ARRIVALDATE));
            deparDateTV.setText(items.get(GoGouConstants.KEY_DEPARTDATE));
            //imageLoader.DisplayImage(song.get(CustomizedListView.KEY_THUMB_URL), thumb_image);
            return vi;
        }
    }

}
