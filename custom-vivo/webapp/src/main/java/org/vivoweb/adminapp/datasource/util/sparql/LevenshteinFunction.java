package org.vivoweb.adminapp.datasource.util.sparql;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.Function;
import org.apache.jena.sparql.function.FunctionEnv;

/**
 * A custom SPARQL function to use Levenshtein distance in SPARQL queries. 
 * 
 * @author swolff
 */
public class LevenshteinFunction implements Function {
    
    private final LevenshteinDistance[] levenshtein = new LevenshteinDistance[1000];

    
    @Override
    public NodeValue exec(Binding binding, ExprList args, String uri, FunctionEnv env) {
        Var valueVar1 = args.get(0).asVar();
        String value1 = binding.get(valueVar1).getLiteralValue().toString();

        Var valueVar2 = args.get(1).asVar();
        String value2 = binding.get(valueVar2).getLiteralValue().toString();
        
        int percent = args.get(2).getConstant().getInteger().intValue();
        int threshold = (int) (value1.length() * (100 - percent) / 100);
        
        if (null == levenshtein[threshold]) {
            levenshtein[threshold] = new LevenshteinDistance(threshold);
        }
        int distance = levenshtein[threshold].apply(value1, value2);
        
        return NodeValue.makeBoolean(-1 != distance);
    }

    
    @Override
    public void build(String uri, ExprList args) {
        // do nothing here for performance reason
    }
    
}