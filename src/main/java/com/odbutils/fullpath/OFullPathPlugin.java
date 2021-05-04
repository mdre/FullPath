package com.odbutils.fullpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import com.orientechnologies.orient.core.sql.functions.OSQLFunctionAbstract;
import com.orientechnologies.orient.core.sql.parser.OLocalResultSet;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.plugin.OServerPluginAbstract;

public class OFullPathPlugin extends OServerPluginAbstract {
    private static final long serialVersionUID = 5630472247035117753L;

    private final static Logger LOGGER = Logger.getLogger(OFullPathPlugin.class.getName());
    static {
        // AnsiColorConsoleHandler.init();
        if (LOGGER.getLevel() == null) {
            LOGGER.setLevel(Level.FINEST);
        }
    }
    List<OIdentifiable[]> paths;
    List<List<OIdentifiable>> tmpPaths;
    OElement from;
    OElement to;
    int maxDepth;

    public OFullPathPlugin() {
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
                System.out.println("entrando en el excute!");
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

                // procesamiento de los parámetros
                // si es un subquery, llegan como un ArrayList.
                // verificar que sea de long 1 y tomar el primer elemento
                if ((iParams[0] instanceof ArrayList)) {
                    if (((ArrayList) iParams[0]).size() > 1) {
                        LOGGER.log(Level.FINEST, "param 0: ERROR ArrayList > 1");
                        return null;
                    } else {
                        LOGGER.log(Level.FINEST, "param 0: ArrayList");
                        from = (OElement) (((ArrayList) iParams[0]).get(0));
                    }
                }

                if ((iParams[0] instanceof ORecordId)) {
                    LOGGER.log(Level.FINEST,"param 0: ORecordID" );
                    from = (OElement) (((ORecordId) iParams[0]).getRecord());
                }
                //----------------------------------------------------------------------
                // repetir para iParam[1]
                if ((iParams[1] instanceof ArrayList)) {
                    if (((ArrayList) iParams[1]).size() > 1) {
                        LOGGER.log(Level.FINEST, "param 1: ERROR ArrayList > 1");
                        return null;
                    } else {
                        LOGGER.log(Level.FINEST, "param 0: ArrayList");
                        to = (OElement) (((ArrayList) iParams[1]).get(0));
                    }
                }
                
                if ((iParams[1] instanceof ORecordId)) {
                    LOGGER.log(Level.FINEST,"param 1: ORecordID" );
                    to = (OElement) (((ORecordId) iParams[1]).getRecord());
                }
                //======================================================================
                
                if (from == null || to == null) {
                    LOGGER.log(Level.FINEST,"param distinct OElement");
                    return null;
                }

                if ((iParams.length > 2) && (!(iParams[2] instanceof Integer))) {
                    System.out.println("param 3");
                    return null;
                }

                // inicializar los paths
                paths = new ArrayList<>();
                tmpPaths = new ArrayList<>();
                ArrayList<OIdentifiable> tmpCurrentPath = new ArrayList<>();
                tmpPaths.add(tmpCurrentPath);
                int tmpCurrentPathIdx = 0;

                
                if (iParams.length > 2) {
                    maxDepth = ((Integer) iParams[2]).intValue();
                } else {
                    maxDepth = 20;
                }

                fullpathImpl(from, to, 0, tmpCurrentPathIdx);
                System.out.println("FIN: paths encontrados: " + paths.size());
                return paths;
            }
        });

        OLogManager.instance().info(this, "fullPath function registered");
    }

    private void fullpathImpl(OElement from, OElement to, int startDepth, int tmpCurrentPathIdx) {
        LOGGER.log(Level.FINEST,"from: "+from.toString()+ " -- to: " + to.toString() + "  -- depth: " + startDepth );
        

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
            // verificar el límite de operación
            if (startDepth < maxDepth) {
                startDepth++;
                // si no son iguales, procesar todas las alternativas dependiendo del tipo de
                // elemento que se trate.
                if (from.isEdge()) {
                    LOGGER.log(Level.FINEST, "edge: " + from.toString());
                    // si es un edge, hay que ver desde donde se llegó al elemento para continuar
                    // por el otro lado.
                    // para esto, reviso el último elelemtno agregado y se continúa hacia el otro
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
                    
                    // FIXME: debería analizese si se desea ir en cualquier dirección y los filtros
                    // posibles.
                    for (OEdge e : v.getEdges(ODirection.BOTH)) {
                        fullpathImpl(e, to, startDepth, tmpCurrentPathIdx);
                    }
                }
                // eliminar el último agregado para que el tmp se pueda seguir procesando en el
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
