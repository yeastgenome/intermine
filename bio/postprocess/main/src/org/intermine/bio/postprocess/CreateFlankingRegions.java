package org.intermine.bio.postprocess;

/*
 * Copyright (C) 2002-2015 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;
import org.intermine.bio.util.BioQueries;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.MetaDataException;
import org.intermine.model.bio.Chromosome;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.DataSource;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.GeneFlankingRegion;
import org.intermine.model.bio.Location;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.util.DynamicUtil;

/**
 * Create features to represent flanking regions of configurable distance either side of gene
 * features.  These will be used in overlap queries.
 * @author rns
 *
 */
public class CreateFlankingRegions
{
    private ObjectStoreWriter osw = null;
    private ObjectStore os;
    private DataSet dataSet;
    private DataSource dataSource;
    private Map<Integer, Chromosome> chrs = new HashMap<Integer, Chromosome>();
    private Map<String, String> genes = new HashMap<String, String>();

    /**
     * The sizes in kb of flanking regions to create.
     */
    private static double[] distances = new double[] {0.5, 1, 2}; // ,10

    /**
     * The values strings for up/down stream from a gene.
     */
    private static String[] directions = new String[] {"upstream", "downstream"};

    private static boolean[] includeGenes = new boolean[] {false, true};

    private static final Logger LOG = Logger.getLogger(CreateFlankingRegions.class);

    /**
     * Create a new CreateFlankingRegions object that will operate on the given
     * ObjectStoreWriter.
     *
     * @param osw the ObjectStoreWriter to use when creating/changing objects
     */
    public CreateFlankingRegions(ObjectStoreWriter osw) {
        this.osw = osw;
        this.os = osw.getObjectStore();
        dataSource = (DataSource) DynamicUtil.createObject(Collections.singleton(DataSource.class));
        dataSource.setName("YeastMine");
        try {
            dataSource = os.getObjectByExample(dataSource, Collections.singleton("name"));
        } catch (ObjectStoreException e) {
            throw new RuntimeException(
                    "unable to fetch modMine DataSource object", e);
        }
    }

    /**
     * Iterate over genes in database and create flanking regions.
     * @throws ObjectStoreException if something goes wrong
     */
    public void createFlankingFeatures() throws ObjectStoreException {
        Results results = BioQueries.findLocationAndObjects(os,
                Chromosome.class, Gene.class, false, false, false, 1000);

        dataSet = (DataSet) DynamicUtil.createObject(Collections
                .singleton(DataSet.class));
        dataSet.setName("YeastMine gene flanking regions");
        dataSet.setDescription("Gene flanking regions generated by YeastMine");
        dataSet.setVersion("" + new Date()); // current time and date
        dataSet.setUrl("http://yeastmine.yeastgenome.org");
        dataSet.setDataSource(dataSource);

        Iterator<?> resIter = results.iterator();

        int count = 0;

        osw.beginTransaction();
        while (resIter.hasNext()) {
        	
            ResultsRow<?> rr = (ResultsRow<?>) resIter.next();
            Integer chrId = (Integer) rr.get(0);
            Gene gene = (Gene) rr.get(1);
            Location loc = (Location) rr.get(2);           
           
            String geneId = genes.get(gene.getPrimaryIdentifier());
            
            if(geneId != null){
            	continue;
            }else{
            	genes.put(gene.getPrimaryIdentifier(), "1");
            }
            
            createAndStoreFlankingRegion(getChromosome(chrId), loc, gene);
            createAndStoreBothSidesFlankingRegion(getChromosome(chrId), loc, gene);
            
            if ((count % 1000) == 0) {
                LOG.info("Created flanking regions for " + count + " genes.");
            }
            count++;
        }
        osw.store(dataSet);

        osw.commitTransaction();
    }


    private void createAndStoreFlankingRegion(Chromosome chr, Location geneLoc, Gene gene)
        throws ObjectStoreException {
    	
        // This code can't cope with chromosomes that don't have a length
        if (chr.getLength() == null) {
            LOG.warn("Attempted to create GeneFlankingRegions on a chromosome without a length: "
                    + chr.getPrimaryIdentifier());
            return;
        }

        // If there is Gene.source attribute we are in modMine, only create flanking regions for
        // genes from FlyBase and WormBase, not those generated by submissions.
        ClassDescriptor cld = this.os.getModel().getClassDescriptorByName("Gene");
        if (cld.getAttributeDescriptorByName("source") != null) {
            String source;
            try {
                source = (String) gene.getFieldValue("source");
            } catch (IllegalAccessException e) {
                // This shouldn't happen
                return;
            }
            if (!("FlyBase".equals(source) || "WormBase".equals(source))) {
                return;
            }

            // HACK if modMine don't include genes in regions
            includeGenes = new boolean[] {false};
        }

        for (double distance : distances) {              	
            for (String direction : directions) {            	
                for (boolean includeGene : includeGenes) {
              	
                    String strand = geneLoc.getStrand();
                    // TODO what do we do if strand not set?
                    int geneStart = geneLoc.getStart().intValue();
                    int geneEnd = geneLoc.getEnd().intValue();
                    int chrLength = chr.getLength().intValue();

                    // gene touches a chromosome end so there isn't a flanking region
                    if ((geneStart <= 1) || (geneEnd >= chrLength)) {
                        continue;
                    }

                    GeneFlankingRegion region = (GeneFlankingRegion) DynamicUtil
                    .createObject(Collections.singleton(GeneFlankingRegion.class));
                    Location location = (Location) DynamicUtil
                    .createObject(Collections.singleton(Location.class));

                    region.setDistance("" + distance + "kb");
                    region.setDirection(direction);
                    try {
                        PostProcessUtil.checkFieldExists(os.getModel(), "GeneFlankingRegion",
                                "includeGene", "Not setting");
                        region.setFieldValue("includeGene", Boolean.valueOf(includeGene));
                    } catch (MetaDataException e) {
                        // GeneFlankingRegion.includeGene not in model so do nothing
                    }
                    region.setGene(gene);
                    region.setChromosome(chr);
                    region.setChromosomeLocation(location);
                    region.setOrganism(gene.getOrganism());
                    region.setPrimaryIdentifier(gene.getPrimaryIdentifier() + " " + distance + "kb "
                            + direction);

                    // this should be some clever algorithm
                    int start =0; int end = 0;

                    if ("upstream".equals(direction) && "1".equals(strand)) {
                        start = geneStart - (int) Math.round(distance * 1000);
                        end = includeGene ? geneEnd : geneStart - 1;
                    } else if ("upstream".equals(direction) && "-1".equals(strand)) {
                        start = includeGene ? geneStart : geneEnd + 1;
                        end = geneEnd + (int) Math.round(distance * 1000);
                    } else if ("downstream".equals(direction) && "1".equals(strand)) {
                        start = includeGene ? geneStart : geneEnd + 1;
                        end = geneEnd + (int) Math.round(distance * 1000);
                    } else if ("downstream".equals(direction) && strand.equals("-1")) {
                        start = geneStart - (int) Math.round(distance * 1000);
                        end = includeGene ? geneEnd : geneStart - 1;
                    } else if(includeGene && "both".equals(direction) && "1".equals(strand)) {  
                    		//start = geneStart - (int) Math.round(distance * 1000); 
                    		 start = geneStart - (int) Math.round(distance * 1000);
                    		//end = geneEnd + (int) Math.round(distance * 1000);
                    		 end = geneEnd + (int) Math.round(distance * 1000);                    		
                    }else if(includeGene && "both".equals(direction) && "-1".equals(strand)){
                    		start = geneStart - (int) Math.round(distance * 1000);
                    		//start = includeGene ? geneStart : geneEnd + 1;
                    		//end = includeGene ? geneEnd : geneStart - 1;
                    		end = geneEnd + (int) Math.round(distance * 1000);
                    }

                    // if the region hangs off the start or end of a chromosome set it to finish
                    // at the end of the chromosome
                    location.setStart(new Integer(Math.max(start, 1)));
                    int e = Math.min(end, chr.getLength().intValue());
                    location.setEnd(new Integer(e));

                    location.setStrand(strand);
                    location.setLocatedOn(chr);
                    location.setFeature(region);

                    region.setLength(new Integer((location.getEnd().intValue()
                            - location.getStart().intValue()) + 1));

                    osw.store(location);
                    osw.store(region);
                }//for includegene
            } //for direction
        } //for distance
        
        
    }
    
    
    private void createAndStoreBothSidesFlankingRegion(Chromosome chr, Location geneLoc, Gene gene)
            throws ObjectStoreException {
        	
            // This code can't cope with chromosomes that don't have a length
            if (chr.getLength() == null) {
                LOG.warn("Attempted to create GeneFlankingRegions on a chromosome without a length: "
                        + chr.getPrimaryIdentifier());
                return;
            }

            String[] directions = new String[] {"both"};
            boolean[] includeGenes = new boolean[] {true};
          
            System.out.println("Gene is .." + gene.getName());
            for (double distance : distances) {              	
                for (String direction : directions) {            	
                    for (boolean includeGene : includeGenes) {
                  	
                        String strand = geneLoc.getStrand();
                        // TODO what do we do if strand not set?
                        int geneStart = geneLoc.getStart().intValue();
                        int geneEnd = geneLoc.getEnd().intValue();
                        int chrLength = chr.getLength().intValue();

                        // gene touches a chromosome end so there isn't a flanking region
                        if ((geneStart <= 1) || (geneEnd >= chrLength)) {
                            continue;
                        }

                        GeneFlankingRegion region = (GeneFlankingRegion) DynamicUtil
                        .createObject(Collections.singleton(GeneFlankingRegion.class));
                        Location location = (Location) DynamicUtil
                        .createObject(Collections.singleton(Location.class));

                        region.setDistance("" + distance + "kb");
                        region.setDirection(direction);
                        try {
                            PostProcessUtil.checkFieldExists(os.getModel(), "GeneFlankingRegion",
                                    "includeGene", "Not setting");
                            region.setFieldValue("includeGene", Boolean.valueOf(includeGene));
                        } catch (MetaDataException e) {
                            // GeneFlankingRegion.includeGene not in model so do nothing
                        }
                        region.setGene(gene);
                        region.setChromosome(chr);
                        region.setChromosomeLocation(location);
                        region.setOrganism(gene.getOrganism());
                        region.setPrimaryIdentifier(gene.getPrimaryIdentifier() + " " + distance + "kb "
                                + direction);

                        // this should be some clever algorithm
                        int start =0; int end = 0;

                        if(includeGene && "both".equals(direction) && "1".equals(strand)) {  
                        		 start = geneStart - (int) Math.round(distance * 1000);
                        		 end = geneEnd + (int) Math.round(distance * 1000);                    		
                        }else if(includeGene && "both".equals(direction) && "-1".equals(strand)){
                        		start = geneStart - (int) Math.round(distance * 1000);
                        		end = geneEnd + (int) Math.round(distance * 1000);
                        }

                        // if the region hangs off the start or end of a chromosome set it to finish
                        // at the end of the chromosome
                        location.setStart(new Integer(Math.max(start, 1)));
                        int e = Math.min(end, chr.getLength().intValue());
                        location.setEnd(new Integer(e));

                        location.setStrand(strand);
                        location.setLocatedOn(chr);
                        location.setFeature(region);

                        region.setLength(new Integer((location.getEnd().intValue()
                                - location.getStart().intValue()) + 1));

                        osw.store(location);
                        osw.store(region);
                    }//for includegene
                } //for direction
            } //for distance
            
            
        }

    private Chromosome getChromosome(Integer chrId) throws ObjectStoreException {
        Chromosome chr = chrs.get(chrId);
        if (chr == null) {
            chr = (Chromosome) os.getObjectById(chrId, Chromosome.class);
            chrs.put(chrId, chr);
        }
        return chr;
    }
}
