/* $This file is distributed under the terms of the license in LICENSE$ */

package edu.cornell.mannlib.vitro.webapp.utils.dataGetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.rdf.model.Model;

import com.fasterxml.jackson.databind.JsonNode;

import edu.cornell.mannlib.vitro.webapp.beans.VClass;
import edu.cornell.mannlib.vitro.webapp.beans.VClassGroup;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.UrlBuilder;
import edu.cornell.mannlib.vitro.webapp.dao.VClassGroupsForRequest;
import edu.cornell.mannlib.vitro.webapp.dao.jena.VClassGroupCache;
import edu.cornell.mannlib.vitro.webapp.web.templatemodels.VClassGroupTemplateModel;

/**
 * This will pass these variables to the template:
 * classGroupUri: uri of the classgroup associated with this page.
 * vClassGroup: a data structure that is the classgroup associated with this page.
 */
public class ClassGroupPageData extends DataGetterBase implements DataGetter{

    private static final Log log = LogFactory.getLog(ClassGroupPageData.class);
    String dataGetterURI;
    String classGroupUri;
    VitroRequest vreq;
    ServletContext context;

    /**
     * Constructor with display model and data getter URI that will be called by reflection.
     */
    public ClassGroupPageData(VitroRequest vreq, Model displayModel, String dataGetterURI){
        this.configure(vreq, displayModel,dataGetterURI);
    }

    /**
     * Configure this instance based on the URI and display model.
     */
    protected void configure(VitroRequest vreq, Model displayModel, String dataGetterURI) {
    	if( vreq == null )
    		throw new IllegalArgumentException("VitroRequest  may not be null.");
        if( displayModel == null )
            throw new IllegalArgumentException("Display Model may not be null.");
        if( dataGetterURI == null )
            throw new IllegalArgumentException("PageUri may not be null.");

        this.vreq = vreq;
        this.context = vreq.getSession().getServletContext();
        this.dataGetterURI = dataGetterURI;
        this.classGroupUri = 	DataGetterUtils.getClassGroupForDataGetter(displayModel, dataGetterURI);
    }


    @Override
    public Map<String, Object> getData(Map<String, Object> pageData) {
    	  HashMap<String, Object> data = new HashMap<String,Object>();
          data.put("classGroupUri", this.classGroupUri);

          VClassGroupsForRequest vcgc = VClassGroupCache.getVClassGroups(vreq);
          List<VClassGroup> vcgList = vcgc.getGroups();
          VClassGroup group = null;
          for( VClassGroup vcg : vcgList){
              if( vcg.getURI() != null && vcg.getURI().equals(classGroupUri)){
                  group = vcg;
                  break;
              }
          }
          if( classGroupUri != null && !classGroupUri.isEmpty() && group == null ){
              /*This could be for two reasons: one is that the classgroup doesn't exist
               * The other is that there are no individuals in any of the classgroup's classes */
              group = vreq.getWebappDaoFactory().getVClassGroupDao().getGroupByURI(classGroupUri);
              if( group != null ){
                  List<VClassGroup> vcgFullList = vreq.getWebappDaoFactory().getVClassGroupDao()
                      .getPublicGroupsWithVClasses(false, true, false);
                  for( VClassGroup vcg : vcgFullList ){
                      if( classGroupUri.equals(vcg.getURI()) ){
                          group = vcg;
                          break;
                      }                                
                  }

                  setAllClassCountsToZero(group);

                  log.debug("Retrieved class group " + group.getURI()
                        + " and returning to template");
                  if (log.isDebugEnabled()) {
                      List<VClass> groupClasses = group.getVitroClassList();
                      for (VClass v : groupClasses) {
                        log.debug("Class " + v.getName() + " - " + v.getURI()
                                + " has " + v.getEntityCount() + " entities");
                      }
                  }
              }else{
                  throw new RuntimeException("classgroup " + classGroupUri + " does not exist in the system");
              }
          }

          data.put("vClassGroup", group);  //may put null

          // adminapp addition
          // We have override this Java class to put the class group label and URI in the data map
          // because Freemarker is apparently a dumb piece of garbage that assumes that because
          // an object implements a collections interface (e.g. the group) it can't possibly also 
          // have other methods we might like to call in addition
          data.put("vClassGroupURI", group.getURI());
          data.put("vClassGroupPublicName", group.getPublicName());

          //This page level data getters tries to set its own template,
          // not all of the data getters need to do this.
          data.put("bodyTemplate", "page-classgroup.ftl");

          //Also add data service url
          //Hardcoding for now, need a more dynamic way of doing this
          data.put("dataServiceUrlIndividualsByVClass", this.getDataServiceUrl());
          return data;
    }


    public static VClassGroupTemplateModel getClassGroup(String classGroupUri, ServletContext context, VitroRequest vreq){

        VClassGroupsForRequest vcgc = VClassGroupCache.getVClassGroups(vreq);
        List<VClassGroup> vcgList = vcgc.getGroups();
        VClassGroup group = null;
        for( VClassGroup vcg : vcgList){
            if( vcg.getURI() != null && vcg.getURI().equals(classGroupUri)){
                group = vcg;
                break;
            }
        }

        if( classGroupUri != null && !classGroupUri.isEmpty() && group == null ){
            /*This could be for two reasons: one is that the classgroup doesn't exist
             * The other is that there are no individuals in any of the classgroup's classes */
            group = vreq.getWebappDaoFactory().getVClassGroupDao().getGroupByURI(classGroupUri);
            if( group != null ){
                List<VClassGroup> vcgFullList = vreq.getWebappDaoFactory().getVClassGroupDao()
                    .getPublicGroupsWithVClasses(false, true, false);
                for( VClassGroup vcg : vcgFullList ){
                    if( classGroupUri.equals(vcg.getURI()) ){
                        group = vcg;
                        break;
                    }                                
                }
                setAllClassCountsToZero(group);
            }else{
                log.error("classgroup " + classGroupUri + " does not exist in the system");
                return null;
            }
        }

        return new VClassGroupTemplateModel(group);
    }

  //Get data service
    public String getDataServiceUrl() {
    	return UrlBuilder.getUrl("/dataservice?getRenderedSearchIndividualsByVClass=1&vclassId=");
    }


    /**
     * For processing of JSONObject
     */
    //Currently empty, TODO: Review requirements
    public JsonNode convertToJSON(Map<String, Object> dataMap, VitroRequest vreq) {
    	return null;
    }
    protected static void setAllClassCountsToZero(VClassGroup vcg){
        for(VClass vc : vcg){
            vc.setEntityCount(0);
        }
    }
}
