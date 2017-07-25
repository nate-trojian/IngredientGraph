var express = require("express");
var neo4j = require('neo4j');
var app = express();

app.set('port', process.env.PORT || 8080);
var db = new neo4j.GraphDatabase(process.env['GRAPHENEDB_URL']);

app.post("/query", function(req, res) {
    var query = req.body;
    //https://neo4j.com/developer/guide-data-visualization/#_howto_graph_visualization_step_by_step
});