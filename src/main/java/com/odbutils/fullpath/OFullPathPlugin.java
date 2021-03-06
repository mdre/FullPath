package com.odbutils.fullpath;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.command.script.transformer.result.OResultTransformer;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.tool.importer.OAbstractCollectionConverter;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.ODirection;
import com.orientechnologies.orient.core.record.OEdge;
import com.orientechnologies.orient.core.record.OElement;
import com.orientechnologies.orient.core.record.OVertex;
import com.orientechnologies.orient.core.sql.OSQLEngine;
import com.orientechnologies.orient.core.sql.executor.OInternalResultSet;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.functions.OSQLFunctionAbstract;
import com.orientechnologies.orient.core.sql.parser.OLocalResultSet;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.plugin.OServerPluginAbstract;


import groovy.util.logging.Log;

public class OFullPathPlugin extends OServerPluginAbstract {
    private static final long serialVersionUID = 5630472247035117753L;

    private final static Logger LOGGER = Logger.getLogger(OFullPathPlugin.class.getName());
    static {
        // AnsiColorConsoleHandler.init();
        if (LOGGER.getLevel() == null) {
            LOGGER.setLevel(Level.INFO);
        }
    }
    List<OIdentifiable[]> paths;
    List<List<OIdentifiable>> tmpPaths;
    OElement from;
    OElement to;
    int maxDepth;
    ODirection direction;
    private String version;
    List<String> include = null;
    List<String> exclude = null;
    List<String> includeEdges = null;
    List<String> excludeEdges = null;
    List<String> includeClasses = null;
    List<String> excludeClasses = null;


    public OFullPathPlugin() {
        version = new Scanner(getClass().getResourceAsStream("/version.txt")).useDelimiter("\\Z").next();
        LOGGER.log(Level.FINEST,"Version: " + version );
    }

    @Override
    public String getName() {
        return "fullPath-plugin";
    }

    @Override
    public void startup() {
        super.startup();
        OSQLEngine.getInstance().registerFunction("fullPath", new OSQLFunctionAbstract("fullPath", 2, 4) {
            @Override
            public String getSyntax() {
                return "fullPath(<fromRID>, <toRID> [, <maxDepth> [, <condition>])";
            }

            @Override
            public Object execute(Object iThis, OIdentifiable iCurrentRecord, Object iCurrentResult,
                    final Object[] iParams, OCommandContext iContext) {
                LOGGER.log(Level.FINEST, "entrando en el excute!");
                LOGGER.log(Level.FINEST, version );

                from = null;
                to = null;
                if (LOGGER.getLevel() == Level.FINEST) {
                    for (int i = 0; i < iParams.length; i++) {
                        Object p = iParams[i];
                        LOGGER.log(Level.FINEST, ">>> class:" + p.getClass() + " : value:" + p + "<<<");
                        // OLogManager.instance().info(this, "" + p.getClass() + ": " + p);
                    }
                }

                if (iParams[0] == null || iParams[1] == null) {
                    LOGGER.log(Level.FINEST, "missing parameters!!");
                    return null;
                }

                // procesamiento de los par??metros
                // si es un subquery, llegan como un ArrayList.
                // verificar que sea de long 1 y tomar el primer elemento
                if ((iParams[0] instanceof ArrayList)) {
                    if (((ArrayList) iParams[0]).size() > 1) {
                        LOGGER.log(Level.FINEST, "param 0: ERROR ArrayList > 1");
                        return null;
                    } else {
                        from = ((OResult)(((ArrayList) iParams[0]).get(0))).getElement().get();
                        LOGGER.log(Level.FINEST, "param 0: ArrayList - from: "+from);
                    }
                } else if ((iParams[0] instanceof OResult)) {
                    OResult orF = (OResult)iParams[0];
                    from = orF.getElement().get();
                
                } else if ((iParams[0] instanceof ORecordId)) {
                    LOGGER.log(Level.FINEST,"param 0: ORecordID" );
                    from = (OElement) (((ORecordId) iParams[0]).getRecord());
                } else {
                    LOGGER.log(Level.SEVERE, "NOT SUPPORTED TYPE: "+iParams[0].getClass().getName());
                    return null;
                }
                //----------------------------------------------------------------------
                // repetir para iParam[1]
                if ((iParams[1] instanceof ArrayList)) {
                    if (((ArrayList) iParams[1]).size() > 1) {
                        LOGGER.log(Level.FINEST, "param 1: ERROR ArrayList > 1");
                        return null;
                    } else {
                        to = ((OResult)(((ArrayList) iParams[1]).get(0))).getElement().get();
                        LOGGER.log(Level.FINEST, "param 1: ArrayList - to: "+to);
                    }
                } else if ((iParams[1] instanceof OResult)) {
                    OResult orF = (OResult)iParams[1];
                    from = orF.getElement().get();
                
                } if ((iParams[1] instanceof ORecordId)) {
                    to = (OElement) (((ORecordId) iParams[1]).getRecord());
                    LOGGER.log(Level.FINEST,"param 1: ORecordID - to: "+to );
                }
                //======================================================================
                
                if (from == null || to == null) {
                    LOGGER.log(Level.FINEST,"param distinct OElement");
                    return null;
                }
                //======================================================================

                // An??lisis de los par??mtros opcionales
                // maxDepth
                if ((iParams.length > 2) && (!(iParams[2] instanceof Map))) {
                    LOGGER.log(Level.SEVERE, "parameters must be a passed in a Map");
                    LOGGER.log(Level.FINEST, iParams[2].getClass().getName() );
                    return null;
                }

                include = null;
                exclude = null;
                maxDepth = 100;
                direction = ODirection.BOTH;
                
                if (iParams.length > 2) {
                    HashMap<String,Object> param = (HashMap)iParams[2];

                    LOGGER.log(Level.FINEST, ""+param);

                    // recuperar el maxDepth
                    if (param.containsKey("maxDepth")) {
                        maxDepth = (int)param.get("maxDepth");
                        LOGGER.log(Level.FINEST,"maxDepth: "+maxDepth );
                    }

                    if (param.containsKey("direction")) {
                        String d = param.get("direction").toString();
                        LOGGER.log(Level.FINEST,"direction: "+direction );
                        if (d.equalsIgnoreCase("IN")) {
                            direction = ODirection.IN;
                        } else if (d.equalsIgnoreCase("OUT")) {
                            direction = ODirection.OUT;
                        } else if (d.equalsIgnoreCase("BOTH")) {
                            direction = ODirection.BOTH;
                        } else {
                            LOGGER.log(Level.SEVERE, "direction must be IN, OUT or BOTH");    
                        }
                    }

                    if (param.containsKey("include")) {
                        LOGGER.log(Level.FINEST, param.get("include").getClass().getName());
                        include = (ArrayList)param.get("include");
                        LOGGER.log(Level.FINEST,"include: "+include );
                    }

                    if (param.containsKey("exclude")) {
                        exclude = (ArrayList)param.get("exclude");
                        LOGGER.log(Level.FINEST,"exclude: "+exclude );
                    }

                    //----------------------------------------------------------------------
                    if (param.containsKey("includeEdges")) {
                        LOGGER.log(Level.FINEST, param.get("includeEdges").getClass().getName());
                        includeEdges = (ArrayList)param.get("includeEdges");
                        LOGGER.log(Level.FINEST,"includeEdges: "+include );
                    }

                    if (param.containsKey("excludeEdges")) {
                        excludeEdges = (ArrayList)param.get("excludeEdges");
                        LOGGER.log(Level.FINEST,"excludeEdges: "+exclude );
                    }

                    //----------------------------------------------------------------------
                    if (param.containsKey("includeClasses")) {
                        LOGGER.log(Level.FINEST, param.get("includeClasses").getClass().getName());
                        includeClasses = (ArrayList)param.get("includeClasses");
                        
                        // para cada clase incluida, recuperar todas las subclases.
                        ArrayList<String> subCIn = new ArrayList<>(); 
                        for (String clase : includeClasses) {
                            subCIn.addAll(iContext.getDatabase().getClass(clase).getAllSubclasses().stream().map(c->c.getName()).collect(Collectors.toList()));
                        }
                        includeClasses.addAll(subCIn);
                        LOGGER.log(Level.FINEST,"includeClasses: "+include );
                    }

                    if (param.containsKey("excludeClasses")) {
                        excludeClasses = (ArrayList)param.get("excludeClasses");

                        // para cada clase incluida, recuperar todas las subclases.
                        ArrayList<String> subCEx = new ArrayList<>(); 
                        for (String clase : excludeClasses) {
                            subCEx.addAll(iContext.getDatabase().getClass(clase).getAllSubclasses().stream().map(c->c.getName()).collect(Collectors.toList()));
                        }

                        excludeClasses.addAll(subCEx);

                        LOGGER.log(Level.FINEST,"excludeClasses: "+exclude );
                    }

                }

                
                // inicializar los paths
                paths = new ArrayList<>();
                tmpPaths = new ArrayList<>();
                ArrayList<OIdentifiable> tmpCurrentPath = new ArrayList<>();
                tmpPaths.add(tmpCurrentPath);
                int tmpCurrentPathIdx = 0;

                

                fullpathImpl(from, to, 1, tmpCurrentPathIdx);
                System.out.println("FIN: paths encontrados: " + paths.size());
                return paths;
            }
        });

        OLogManager.instance().info(this, "fullPath function registered");
    }

    private void fullpathImpl(OElement from, OElement to, int startDepth, int tmpCurrentPathIdx) {
        LOGGER.log(Level.FINEST,"from: "+from.toString()+ " -- to: " + to.toString() + "  -- depth: " + startDepth );
        //======================================================================
        // validar los filtros
        //======================================================================
        // validar que el from no est?? en la lista de exclusi??n.
        if (exclude != null && from.getSchemaType().isPresent() 
            && exclude.contains(from.getSchemaType().get().getName())) {
            LOGGER.log(Level.FINEST, ">>>>>> Excluido: "+from);
            return;
        }

        // validar que el from est?? en la lista de inclusiones.       
        if (include!=null && from.getSchemaType().isPresent() 
            && !include.contains(from.getSchemaType().get().getName())) {
                LOGGER.log(Level.FINEST, ">>>>>> No incluido: "+from);
            return;
        }
        //----------------------------------------------------------------------
        //-- include/exclude Edges
        if (excludeEdges != null && from.isEdge() && from.getSchemaType().isPresent() 
            && exclude.contains(from.getSchemaType().get().getName())) {
            LOGGER.log(Level.FINEST, ">>>>>> Excluido: "+from);
            return;
        }

        // validar que el from est?? en la lista de inclusiones.       
        if (includeEdges!=null && from.isEdge() && from.getSchemaType().isPresent() 
            && !include.contains(from.getSchemaType().get().getName())) {
                LOGGER.log(Level.FINEST, ">>>>>> No incluido: "+from);
            return;
        }
        //----------------------------------------------------------------------
        //-- include/exclude Classes
        if (excludeClasses != null && from.getSchemaType().isPresent() 
            && exclude.contains(from.getSchemaType().get().getName())) {
            LOGGER.log(Level.FINEST, ">>>>>> Excluido: "+from);
            return;
        }

        // validar que el from est?? en la lista de inclusiones.       
        if (includeClasses!=null && from.getSchemaType().isPresent() 
            && !include.contains(from.getSchemaType().get().getName())) {
                LOGGER.log(Level.FINEST, ">>>>>> No incluido: "+from);
            return;
        }

        //======================================================================
        // si el from es igual al to, entonces llegamos y podemos cerrar el path.
        if (from.equals(to)) {
            LOGGER.log(Level.FINEST,"Path completo!!!" );

            // agregar el from para completar...
            tmpPaths.get(tmpCurrentPathIdx).add(from);
            LOGGER.log(Level.FINEST, Arrays.toString(tmpPaths.get(tmpCurrentPathIdx).toArray()));
            paths.add(tmpPaths.get(tmpCurrentPathIdx).toArray(new OElement[tmpPaths.get(tmpCurrentPathIdx).size()]));
            
            // y removerlo para seguir
            tmpPaths.get(tmpCurrentPathIdx).remove(tmpPaths.get(tmpCurrentPathIdx).size() - 1);
        } else {
            List<OIdentifiable> tmp = tmpPaths.get(tmpCurrentPathIdx);
            // si el OElement ya forma parte del tmp, retornar para no genear loops,
            if (tmp.contains(from)) {
                return;
            }

            OElement next = null;
            // verificar el l??mite de operaci??n
            if (startDepth < maxDepth) {
                startDepth++;
                // si no son iguales, procesar todas las alternativas dependiendo del tipo de
                // elemento que se trate.
                if (from.isEdge()) {
                    LOGGER.log(Level.FINEST, "edge: " + from.toString());
                    // si es un edge, hay que ver desde donde se lleg?? al elemento para continuar
                    // por el otro lado.
                    // para esto, reviso el ??ltimo elelemtno agregado y se contin??a hacia el otro
                    // elemento al que apunte el
                    // edge.
                    OEdge e = from.asEdge().get();
                    if (e.getFrom().equals(tmp.get(tmp.size() - 1))) {
                        next = e.getTo();
                    } else {
                        next = e.getFrom();
                    }
                    // agrego el from al path temporal.
                    tmpPaths.get(tmpCurrentPathIdx).add(from);

                    fullpathImpl(next, to, startDepth, tmpCurrentPathIdx);

                } else {
                    // agrego el from al path temporal.
                    tmpPaths.get(tmpCurrentPathIdx).add(from);
                    LOGGER.log(Level.FINEST, "vertex: " + from.toString());
                    // si se trata de un Vertex, procesar todos los edges.
                    OVertex v = from.asVertex().get();
                    
                    // FIXME: deber??a analizese si se desea ir en cualquier direcci??n y los filtros
                    // posibles.
                    for (OEdge e : v.getEdges(ODirection.BOTH)) {
                        fullpathImpl(e, to, startDepth, tmpCurrentPathIdx);
                    }
                }
                // eliminar el ??ltimo agregado para que el tmp se pueda seguir procesando en el
                // paso anterior.
                tmpPaths.get(tmpCurrentPathIdx).remove(tmpPaths.get(tmpCurrentPathIdx).size() - 1);
            }
        }
    }

    @Override
    public void config(OServer oServer, OServerParameterConfiguration[] iParams) {

    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
