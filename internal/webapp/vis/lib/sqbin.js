(function() {

    d3.sqbin = function() {
        var width = 1,
                height = 1,
                s,
                x = d3_sqbinX,
                y = d3_sqbinY,
                dx,
                dy;

        // polyfill Math.trunc()
        function trunc(_x) {
            return _x < 0 ? Math.ceil(_x) : Math.floor(_x);
        }

        function sqbin(points) {
            var binsById = {};

            points.forEach(function(point, i) {
                var py = y.call(sqbin, point, i) / dy;
                var pj = trunc(py);
                var px = x.call(sqbin, point, i) / dx;
                var pi = trunc(px);

                var id = pi + "-" + pj;
                var bin = binsById[id];
                if (bin) bin.push(point); else {
                    bin = binsById[id] = [point];
                    bin.i = pi;
                    bin.j = pj;
                    bin.x = (pi+0.5) * dx;
                    bin.y = (pj+0.5) * dy;
                }
            });

            return d3.values(binsById);
        }

        function square(side) {
            return d3_sqbinCorners.map(function(corner) {
                switch(corner) {
                    case 0:
                    case 4:
                        return [-side/2, -side/2];
                    case 1:
                        return [+side/2, -side/2];
                    case 2:
                        return [+side/2, +side/2];
                    case 3:
                        return [-side/2, +side/2];
                    default:
                        console.error('what happened?');
                }
            });
        }

        sqbin.x = function(_) {
            if (!arguments.length) return x;
            x = _;
            return sqbin;
        };

        sqbin.y = function(_) {
            if (!arguments.length) return y;
            y = _;
            return sqbin;
        };

        sqbin.square = function(side) {
            if (arguments.length < 1) side = s;
            return "M" + square(side).join(" ");
        };

        sqbin.centers = function() {
            console.log('sqbin does not yet implement centers()');
            return [];
//            var centers = [];
//            for (var y = 0, odd = false, j = 0; y < height + r; y += dy, odd = !odd, ++j) {
//                for (var x = odd ? dx / 2 : 0, i = 0; x < width + dx / 2; x += dx, ++i) {
//                    var center = [x, y];
//                    center.i = i;
//                    center.j = j;
//                    centers.push(center);
//                }
//            }
//            return centers;
        };

//        sqbin.mesh = function() {
//            var fragment = square(r).slice(0, 4).join("l");
//            return sqbin.centers().map(function(p) { return "M" + p + "m" + fragment; }).join("");
//        };

        sqbin.size = function(_) {
            if (!arguments.length) return [width, height];
            width = +_[0], height = +_[1];
            return sqbin;
        };

        sqbin.side = function(_) {
            if (!arguments.length) return s;
            s = +_;
            dx = s;
            dy = s;
            return sqbin;
        };

        return sqbin.side(1);
    };

    var d3_sqbinCorners = d3.range(0, 5, 1),
            d3_sqbinX = function(d) { return d.x; },
            d3_sqbinY = function(d) { return d.y; };
})();
