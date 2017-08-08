var express = require("express");
var bodyParser = require('body-parser')
var neo4j = require('neo4j');
var app = express();

function errorHandler (err, req, res, next) {
  res.status(500)
  res.render('error', { error: err })
}

app.use(bodyParser.json())
app.use(errorHandler);

app.set('port', process.env.PORT || 8080);
var db = new neo4j.GraphDatabase(process.env['GRAPHENEDB_URL']);

app.post("/query", function(req, res) {
    var query = req.body;
    //https://neo4j.com/developer/guide-data-visualization/#_howto_graph_visualization_step_by_step
    db.cypherQuery(query, function(err, result){
        if(err) next(err);
    });
});