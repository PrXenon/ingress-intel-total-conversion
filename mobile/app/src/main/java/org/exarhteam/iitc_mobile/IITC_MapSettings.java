package org.exarhteam.iitc_mobile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.Spinner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;

public class IITC_MapSettings implements OnItemSelectedListener, OnItemClickListener, OnItemLongClickListener {
    private final IITC_Mobile mIitc;
    private final ArrayAdapter<String> mHighlighters;
    private final ArrayAdapter<Layer> mBaseLayers;
    private final ArrayAdapter<Layer> mOverlayLayers;
    private final Spinner mSpinnerBaseMap;
    private final Spinner mSpinnerHighlighter;
    private final ListView mListViewOverlayLayers;
    private String mActiveHighlighter;
    private int mActiveLayer;
    private boolean mLoading = true;
    private boolean mDisableListeners = false;

    public IITC_MapSettings(final IITC_Mobile activity) {
        mIitc = activity;

        mHighlighters = new HighlighterAdapter(R.layout.list_item_narrow);
        mBaseLayers = new LayerAdapter(R.layout.list_item_narrow);
        mOverlayLayers = new LayerAdapter(R.layout.checked_textview);

        mHighlighters.setDropDownViewResource(R.layout.list_item_selectable);
        mBaseLayers.setDropDownViewResource(R.layout.list_item_selectable);

        final LayoutInflater inflater = (LayoutInflater) mIitc.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View header = inflater.inflate(R.layout.map_options_header, null);

        mSpinnerHighlighter = header.findViewById(R.id.spinnerHighlighter);
        mSpinnerBaseMap = header.findViewById(R.id.spinnerBaseLayer);
        mListViewOverlayLayers = mIitc.findViewById(R.id.right_drawer);

        mListViewOverlayLayers.addHeaderView(header);

        mSpinnerHighlighter.setAdapter(mHighlighters);
        mSpinnerBaseMap.setAdapter(mBaseLayers);
        mListViewOverlayLayers.setAdapter(mOverlayLayers);

        mSpinnerHighlighter.setOnItemSelectedListener(this);
        mSpinnerBaseMap.setOnItemSelectedListener(this);
        mListViewOverlayLayers.setOnItemClickListener(this);
        mListViewOverlayLayers.setOnItemLongClickListener(this);
    }

    private void setLayer(final Layer layer) {
        if (!mLoading) {
            mIitc.getWebView().loadUrl(
                    "javascript: window.layerChooser.showLayer(" + layer.id + "," + layer.active + ");");
        }
    }

    public void addPortalHighlighter(final String name) {
        mHighlighters.add(name);

        // to select active highlighter. must be called every time because of sorting
        setActiveHighlighter(mActiveHighlighter);
    }

    public void onBootFinished() {
        mLoading = false;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, int position, final long id) {
        if (mDisableListeners) return;
        position--; // The ListView header counts as an item as well.

        final Layer item = mOverlayLayers.getItem(position);
        item.active = !item.active;
        setLayer(item);
        mOverlayLayers.notifyDataSetChanged();
    }

    @Override
    public boolean onItemLongClick(final AdapterView<?> parent, final View view, int position, final long id) {
        if (mDisableListeners || position == 0) return false;
        position--; // The ListView header counts as an item as well.
        mIitc.getWebView().loadUrl(
                "javascript: " +
                        "var data = window.layerChooser._layers[" + mOverlayLayers.getItem(position).id + "];" +
                        "window.layerChooser._onLongClick(data);");

        return true;
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
        if (mLoading) return;
        if (mDisableListeners) return;

        if (parent.equals(mSpinnerHighlighter)) {
            final String name = mHighlighters.getItem(position);
            mIitc.getWebView().loadUrl("javascript: window.changePortalHighlights('" + name + "')");
        } else if (parent.equals(mSpinnerBaseMap)) {
            mBaseLayers.getItem(mActiveLayer).active = false; // set old layer to hidden, but no need to really hide

            final Layer layer = mBaseLayers.getItem(position);
            layer.active = true;
            setLayer(layer);
        }
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
        // ignore
    }

    public void reset() {
        mHighlighters.clear();
        mBaseLayers.clear();
        mOverlayLayers.clear();

        mIitc.getNavigationHelper().setHighlighter(null);

        mLoading = true;
    }

    public void setActiveHighlighter(final String name) {
        mActiveHighlighter = name;

        final int position = mHighlighters.getPosition(mActiveHighlighter);
        if (position >= 0 && position < mHighlighters.getCount()) {
            mSpinnerHighlighter.setSelection(position);
        }

        mIitc.getNavigationHelper().setHighlighter(name);
    }

    public void setLayers(final String base_layer, final String overlay_layer) {
        /*
         * the layer strings have a form like:
         * [{"layerId":27,"name":"MapQuest OSM","active":true},
         * {"layerId":28,"name":"Default Ingress Map","active":false}]
         * Put it in a JSONArray and parse it
         */
        JSONArray base_layers = null;
        JSONArray overlay_layers = null;

        try {
            base_layers = new JSONArray(base_layer);
            overlay_layers = new JSONArray(overlay_layer);
        } catch (final JSONException e) {
            Log.w(e);
            return;
        }

        mDisableListeners = true;

        mActiveLayer = 0;
        mBaseLayers.setNotifyOnChange(false);
        mBaseLayers.clear();
        for (int i = 0; i < base_layers.length(); i++) {
            try {
                final JSONObject layerObj = base_layers.getJSONObject(i);
                final Layer layer = new Layer();

                layer.id = layerObj.getInt("layerId");
                layer.name = layerObj.getString("name");
                layer.active = layerObj.getBoolean("active");

                if (layer.active)
                // getCount() will be the index of the layer we are about to add
                {
                    mActiveLayer = mBaseLayers.getCount();
                }

                mBaseLayers.add(layer);
            } catch (final JSONException e) {
                Log.w(e);
            }
        }
        mBaseLayers.notifyDataSetChanged();
        mSpinnerBaseMap.setSelection(mActiveLayer);

        mOverlayLayers.setNotifyOnChange(false);
        mOverlayLayers.clear();
        for (int i = 0; i < overlay_layers.length(); i++) {
            try {
                final JSONObject layerObj = overlay_layers.getJSONObject(i);
                final Layer layer = new Layer();

                layer.id = layerObj.getInt("layerId");
                layer.name = layerObj.getString("name");
                layer.active = layerObj.getBoolean("active");

                mOverlayLayers.add(layer);
            } catch (final JSONException e) {
                Log.w(e);
            }
        }
        mOverlayLayers.notifyDataSetChanged();

        mDisableListeners = false;
    }

    private class HighlighterAdapter extends ArrayAdapter<String> {
        private final HighlighterComparator mComparator = new HighlighterComparator();

        private HighlighterAdapter(final int resource) {
            super(mIitc, resource);
            clear();
        }

        @Override
        public void add(final String object) {
            super.remove(object); // to avoid duplicates
            super.add(object);
            super.sort(mComparator);
        }

        @Override
        public void clear() {
            super.clear();
            add("No Highlights");// Probably must be the same as window._no_highlighter
        }
    }

    private class HighlighterComparator implements Comparator<String> {
        @Override
        public int compare(final String lhs, final String rhs) {
            // Move "No Highlights" on top. Sort the rest alphabetically
            if (lhs.equals("No Highlights")) {
                return -1000;
            } else if (rhs.equals("No Highlights")) {
                return 1000;
            } else {
                return lhs.compareTo(rhs);
            }
        }
    }

    private class Layer {
        boolean active;
        int id;
        String name;

        @Override
        public String toString() {
            return name;
        }
    }

    private class LayerAdapter extends ArrayAdapter<Layer> {
        public LayerAdapter(final int resource) {
            super(mIitc, resource);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            final Layer item = getItem(position);
            final View view = super.getView(position, convertView, parent);

            if (view instanceof CheckedTextView) {
                ((CheckedTextView) view).setChecked(item.active);
            }
            return view;
        }
    }
}
