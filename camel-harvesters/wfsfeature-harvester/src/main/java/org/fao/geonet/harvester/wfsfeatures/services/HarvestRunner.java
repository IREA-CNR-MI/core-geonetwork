package org.fao.geonet.harvester.wfsfeatures.services;

import com.google.common.collect.Lists;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.harvester.wfsfeatures.event.WfsIndexingEvent;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.utils.Xml;
import org.geonetwork.messaging.JMSMessager;
import org.jdom.Element;
import org.jdom.Namespace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.ArrayList;
import java.util.List;

import static org.fao.geonet.schema.iso19139.ISO19139Namespaces.GCO;
import static org.fao.geonet.schema.iso19139.ISO19139Namespaces.GMD;

/**
 * Created by fgravin on 10/29/15.
 */

@Controller
public class HarvestRunner {
    @Autowired
    private JMSMessager jmsMessager;

    private static final ArrayList<Namespace> NAMESPACES = Lists.newArrayList(GMD, GCO);


    /**
     * Index a featureType from a wfs service URL and a typename
     *
     * @param uiLang lang
     * @param wfsUrl wfs service url
     * @param featureType feature type name
     * @param webRequest
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/{uiLang}/wfs.harvest")
    @ResponseBody
    public JSONObject indexWfs(
            @PathVariable String uiLang,
            @RequestParam("url") String wfsUrl,
            @RequestParam("typename") String featureType,
            NativeWebRequest webRequest) throws Exception {

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("indexedFeatures", sendMessage(null, wfsUrl, featureType));

        return result;
    }

    /**
     * Index all featureTypes that are contained in the metadata.
     * Feature types are extracted from the online resources of the metedata.
     * The protocol should be WFS, the typename is from gmd:name element, and
     * the url from the gmd:linkage.
     *
     * @param uiLang lang
     * @param uuid uuid of the metadata
     * @param webRequest
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/{uiLang}/wfs.harvest/{uuid}")
    @ResponseBody
    public JSONObject indexWfs(
            @PathVariable String uiLang,
            @PathVariable String uuid,
            NativeWebRequest webRequest) throws Exception {
        ConfigurableApplicationContext appContext = ApplicationContextHolder.get();
        DataManager dataManager = appContext.getBean(DataManager.class);

        final String id = dataManager.getMetadataId(uuid);
        Element xml = dataManager.getMetadata(id);

        // TODO: This is ISO19139 specific
        // Use Lucene field instead ?
        final List<Element> wfsLinkages =
                Lists.newArrayList((Iterable<? extends Element>) Xml.selectNodes(xml,
                        "*//gmd:CI_OnlineResource[contains(" +
                                "gmd:protocol/gco:CharacterString, 'WFS')]",
                        NAMESPACES));

        JSONArray a = new JSONArray();
        for (Element element : wfsLinkages) {
            Element linkageElt = (Element)Xml.selectSingle(element, "gmd:linkage/gmd:URL", NAMESPACES);
            Element ftElt = (Element)Xml.selectSingle(element, "gmd:name/gco:CharacterString", NAMESPACES);

            a.add(sendMessage(uuid, linkageElt.getText(), ftElt.getText()));
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("indexedFeatures", a);

        return result;
    }

    private JSONObject sendMessage(final String uuid, final String wfsUrl, final String featureType) {
        ConfigurableApplicationContext appContext = ApplicationContextHolder.get();
        WfsIndexingEvent event = new WfsIndexingEvent(appContext, uuid, wfsUrl, featureType);
        // TODO: Messages should be node specific eg. srv channel ?
        jmsMessager.sendMessage("harvest-wfs-features", event);

        JSONObject j = new JSONObject();
        j.put("url", wfsUrl);
        j.put("featureType", featureType);

        return j;
    }
}