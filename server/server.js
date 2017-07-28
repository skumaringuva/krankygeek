var socketIO = require('/usr/local/lib/node_modules/socket.io');
var server = require('http').createServer().listen(5000, '0.0.0.0');
var io = socketIO.listen(server);
//var io = require('/root/node_modules/socket.io/lib/socket.io'); 
//var express = require('/usr/local/lib/node_modules/express/lib/express.js'‌​);
// Super simple server:
//  * One room only. 
///usr/local/lib/node_modules/socket.io/lib
//  * We expect two people max. 
//  * No error handling.

io.sockets.on('connection', function (client) {
    console.log('new connection: ' + client.id);

    client.on('offer', function (details) {
        client.broadcast.emit('offer', details);
        console.log('offer: ' + JSON.stringify(details));
    });

    client.on('answer', function (details) {
        client.broadcast.emit('answer', details);
        console.log('answer: ' + JSON.stringify(details));
    });
    
    client.on('candidate', function (details) {
        client.broadcast.emit('candidate', details);
        console.log('candidate: ' + JSON.stringify(details));
    });

    // Here starts evertyhing!
    // The first connection doesn't send anything (no other clients)
    // Second connection emits the message to start the SDP negotation
    client.broadcast.emit('createoffer', {});
});

