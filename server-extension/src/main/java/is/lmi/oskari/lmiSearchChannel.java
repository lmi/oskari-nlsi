package is.lmi.oskari.search;

import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.IllegalSearchCriteriaException;
import fi.mml.portti.service.search.SearchCriteria;
import fi.mml.portti.service.search.SearchResultItem;
import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.domain.geo.Point;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.JSONHelper;
import fi.nls.oskari.util.PropertyUtil;
import org.geotools.referencing.CRS;
import fi.nls.oskari.map.geometry.ProjectionHelper;
import fi.nls.oskari.search.channel.SearchChannel;
import fi.nls.oskari.util.ConversionHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.List;
import java.net.URLEncoder;
import java.util.Iterator;

@Oskari(lmiSearchChannel.ID)
public class lmiSearchChannel extends SearchChannel {

    private String serviceURL = null;
    private Logger log = LogFactory.getLogger(this.getClass());
    private static final String PROPERTY_SERVICE_URL = "search.channel.LMI_CHANNEL.service.url";
    private boolean forceCoordinateSwitch = false;

    public static final String ID = "LMI_CHANNEL";
    public final static String SERVICE_SRS = "EPSG:3057";

    @Override
    public void init() {
        super.init();
        serviceURL = PropertyUtil.getOptional(PROPERTY_SERVICE_URL);
    }

    private JSONArray getData(SearchCriteria searchCriteria) throws Exception {
        if (serviceURL == null) {
            log.warn("ServiceURL not configured. Add property with key",PROPERTY_SERVICE_URL);
            return new JSONArray();
        }
	StringBuffer buf = new StringBuffer( serviceURL );
	if( serviceURL.indexOf( "?" ) > 0 )
		buf.append( "&format=json" );
	else
		buf.append( "?format=json" );

        buf.append("&accept-language=");
        buf.append(searchCriteria.getLocale());
        int maxResults = searchCriteria.getMaxResults();
        if (maxResults > 0) {
            buf.append("&limit="+Integer.toString(maxResults));
        }
        buf.append("&q=");
        buf.append(URLEncoder.encode(searchCriteria.getSearchString(),"UTF-8"));
        String data = IOHelper.readString(getConnection(buf.toString()));
        log.debug("DATA: " + data);

	JSONArray	jarry = new JSONArray();

	try {
		JSONObject obj = new JSONObject( data );
		jarry = obj.getJSONArray("result");
/***		Iterator x = json.keys();
		while( x.hasNext() ) {
			String key = (String) x.next();
			jarry.put(json.get(key));
		}
****/
        } catch (Exception e) {
            log.error(e, "json parse error: ", 
                    searchCriteria.getSearchString(), 
                    "- ServiceURL used was:", serviceURL);
        }

	return jarry;
    }

    public ChannelSearchResult doSearch(SearchCriteria searchCriteria)
            throws IllegalSearchCriteriaException {
        if(serviceURL == null) {
            log.warn("ServiceURL not configured. Add property with key", PROPERTY_SERVICE_URL);
            return null;
        }
        ChannelSearchResult searchResultList = new ChannelSearchResult();

        String srs = searchCriteria.getSRS();
        if( srs == null ) {
        	srs = "EPSG:3067";
        }

        try {
            CoordinateReferenceSystem sourceCrs = CRS.decode(SERVICE_SRS);
            CoordinateReferenceSystem targetCrs = CRS.decode(srs);
            final JSONArray data = getData(searchCriteria);

            final boolean reverseCoordinates = false;
/* rember box (SetWestBoundLongitute ...) */

            for (int i = 0; i < data.length(); i++) {
                JSONObject dataItem = data.getJSONObject(i);
                SearchResultItem item = new SearchResultItem();

	            item.setChannelId( ID );
                //item.setTitle(dataItem.getString("name")+" "+dataItem.getString("nameid")+"-"+dataItem.getString("id"));
                item.setTitle(dataItem.getString("name"));
                item.setLocationName( dataItem.getString("name"));
                item.setVillage(dataItem.getString("sveitarfelag"));
                item.setResourceId(dataItem.getString("nameid"));
                item.setDescription(dataItem.getString( "layer" ));
//		item.setType("1.0");
//		item.setLocationTypeCode("1.0");
		item.setType(dataItem.getString("nafnberi" ));
		item.setLocationTypeCode(dataItem.getString("nafnberi"));
                item.setLat(dataItem.getString("y"));
                item.setLon(dataItem.getString( "x" ));
                item.setSouthBoundLatitude(dataItem.getString( "ymin" ));
                item.setWestBoundLongitude(dataItem.getString( "xmin" ));
                item.setNorthBoundLatitude(dataItem.getString( "ymax" ));
                item.setEastBoundLongitude(dataItem.getString( "xmax" ));
                // FIXME: add more automation on result rank scaling
                try {
                    item.setRank(100*(int)Math.round(dataItem.getDouble("importance")));
                } catch(JSONException e) {
                    item.setRank(0);
                }
                searchResultList.addItem(item);
                // convert to map projection
                final Point point = ProjectionHelper.transformPoint(
                        ConversionHelper.getDouble(item.getLon(), -1),
                        ConversionHelper.getDouble(item.getLat(), -1),
                        sourceCrs,
                        targetCrs);
                if(point == null) {
                    item.setLon("");
                    item.setLat("");
                    continue;
                }
                // switch order again after making the transform if necessary
                if(!forceCoordinateSwitch && (reverseCoordinates  || ProjectionHelper.isFirstAxisNorth(targetCrs))) {
                    point.switchLonLat();
                }
                item.setLon(point.getLon());
                item.setLat(point.getLat());
        	log.debug("POINT: " + point);
	    }

        } catch (Exception e) {
            // never actually throws IllegalSearchCriteriaException
            // since its thrown only by QueryParser.parse() which is not used here
            // so we can catch all
            log.error(e, "Search resulted in an exception for query: ", 
                    searchCriteria.getSearchString(), 
                    "- ServiceURL used was:", serviceURL);
        }

        return searchResultList;
    }
}
