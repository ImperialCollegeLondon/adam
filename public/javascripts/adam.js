/*global $, _, data, user, users*/
//TODO stop polluting global namespace (_.mixin()?)
function init(userData) {
    user = userData;
    user.groups.sort();
    $('#prefs input').each(function(index, value) {
        $(value).val(user[value.name] || '');
    });
}
function param(object) {
    return $.param(object);
}
function bytesToSize(bytes) {
    var sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    if (bytes === 0) {
        return 'n/a';
    }
    var i = Math.floor(Math.log(bytes) / Math.log(1024));
    return Math.round(bytes / Math.pow(1024, i), 2) + sizes[i];
}
function localeString(datetime) {
    return new Date(datetime * 1000).toLocaleDateString();
}
function encode(path) {
    return _.map(path.split('/'), function(value){ return encodeURIComponent(value); }).join('/');
}
function strip(title) {
    var match = title.match(/^\[?(.*?)\]?\.$/);
    return match !== null ? match[1] : title;
}
function splitLast(string, separator) {
    return string.split(separator).pop();
}
function initialCap(str) {
   return str.substr(0, 1).toUpperCase() + str.substr(1);
}
function facetValue(facet) {
    return $('#search [name=' + facet + ']').val();
}
function facetEquals(facet, value) {
    return value === facetValue(facet);
}
function icon(document) {
    return document.subtype ? document.subtype.toLowerCase() : document.type;
}
function scale(array, factor) {
    return _.map(array, function(num) { return num / factor * 100; });
}
function sum(array) {
    return _.reduce(array, function(memo, num) { return memo + num; }, 0);
}
function poll() {
    $.ajax({
        url: 'ws/poll',
        success: function(data) {
            if (data.update) {
                $('#search').submit();
            }
            poll();
        }
    });
}
function updateAnalytics() {
    $.get('ws/stats', function(data) {
        var chd, chl, type_chart_url;
        if (data.types.length) {
            chd = _.pluck(data.types, 'type');
            chl = _.pluck(data.types, 'count');
            chl = scale(chl, sum(chl));
            type_chart_url = 'http://chart.apis.google.com/chart?chco=696969&chs=350x225&cht=p&chd=t:' + chl.join(',') + '&chl=' + chd.join('|');
            $('#chart_1').html('<img src="' + type_chart_url + '">');
        } else {
            $('#chart_1').empty();
        }
        if (data.users.length) {
            chd = _.pluck(data.users, '_id');
            chl = _.pluck(data.users, 'value');
            chl = scale(chl, sum(chl));
            type_chart_url = 'http://chart.apis.google.com/chart?chco=696969&chs=350x225&cht=p&chd=t:' + chl.join(',') + '&chl=' + chd.join('|');
            $('#chart_2').html('<img src="' + type_chart_url + '">');
        } else {
            $('#chart_2').empty();
        }
        if (data.dates.length) {
            var day_counts = _.reduce(data.dates, function(memo, row) { memo[row._id] = row.value; return memo; }, {});
            var days = [];
            var counts = [];
            var now;
            var i;
            for (now = new Date().getTime(), i = now - 6 * 24 * 60 * 60 * 1000; i <= now; i += 24 * 60 * 60 * 1000) {
                var date = new Date(i);
                days.push(date.toDateString().split(' ')[0]);//e.g. 'Wed'
                counts.push(day_counts[date.getDay()] || 0);
            }
            counts = scale(counts, _.max(counts));
            var dates_chart_url = 'http://chart.apis.google.com/chart?chco=696969&chxl=0:|' + days.join('|') + '&chxt=x&chbh=a&chs=300x225&cht=bvg&chd=t:' + counts.join(',');
            $('#chart_3').html('<img src="' + dates_chart_url + '">');
        } else {
            $('#chart_3').empty();
        }
        $('#stats').html($('#statstemplate').tmpl(data.summary));
    });
}
$(function() {
    if (!$.browser.webkit) {
        $('button.clear').on('click', function() {
            $(this).prev().val('').trigger('search');
        }).show();
    };
    $('#files').delegate('a', 'click', function() {
        if (/\/$/.test(this.href)) {
            $('#files').load(this.href);
            return false;
        }
    });
    $('#prefs input').change(function() {
        $(this).closest('form').ajaxSubmit({
            success: function(data) { init(data); },
            error: function() { init(user); }
        });
        return false;
    });
    $('#sort :radio, #starred').change(function() {
        $('#search input[name=sort]').val($(':radio[name="sort"]:checked').val());
        $('#search input[name=starred]').val($(':checkbox[name="starred"]:checked').val());
        $('#search input[name=page]').val(0);
        $('#search').submit();
    });
    $('#results').delegate('div.type > a', 'click', function() {
        $('div.type').removeClass('selected');
        $(this).parent().addClass('selected');
        $('#preview').html($('#detailspartial').tmpl($(this).parent().data('document')));
    });
    $('#results, #preview').delegate('form', 'submit', function() {
        $(this).ajaxSubmit();
        return false;
    });
    $('#preview').delegate('a.mobile', 'click', function() {
        $('<img src="http://chart.apis.google.com/chart?cht=qr&chs=350x350&chl=' + encodeURI(this.href) + '">').dialog({modal: true, width: 375});
        return false;//need false here because a has a real href (rather than #)
    }).delegate('select', 'change', function() {
        $(this).parent().ajaxSubmit(function(data) {
            if (data.recipient) {
                //$.jGrowl('Item shared with ' + result.recipient);
                $().toastmessage('showSuccessToast', 'Item shared with ' + data.recipient);
            } else if (data.link) {
                $('#sharetemplate').tmpl(data).dialog({width: 512, modal: true});
            } else {
                //$.jGrowl('Item unshared');
                $().toastmessage('showSuccessToast', 'Item unshared');
            }
            //$('#template').tmpl(result.item).replaceAll('#' + result.item._id.$oid);
            //$('#' + result.item._id.$oid + ' > div').toggle();
        });
    }).delegate('.similar > a', 'click', function() {
        var a = this;
        $.getJSON('http://entrezajax.appspot.com/elink+esummary?callback=?', {
            id: $(this).parent().data('pmid'),
            apikey: '1d7c2923c1684726dc23d2901c4d8157',
            db: 'pubmed',
            max: 6
        }, function(data) {
            data.result.shift();
            $('#similartemplate').tmpl(data).replaceAll(a);
        });
    });
    $('#paging').delegate('a', 'click', function() {
        $('#search input[name=page]').val($(this).data('page'));
        $('#search').submit();
    });
    $('#filter').delegate('a.facet', 'click', function() {
       $('#search input[name=' + $(this).data('facet') + ']').val($(this).data('facetValue'));
       $('#search input[name=page]').val(0);
       $('#search').submit();
    }).delegate('a.expand', 'click', function() {
        $(this).parent().toggleClass('expanded');
    });
    $('#upload-location').change(function() {
        $('#html5_uploader').pluploadQueue().settings.multipart_params = {path: $('option:selected', this).val()};
    });
    $('#search').submit(function() {
        $('#search input[name=q]').val($('#q').val());
        $('#q').autocomplete('close');
    }).ajaxForm({dataType: 'json', success: function(data) {
        var selectedID = $('div.selected').attr('id');
        $('#results').empty();
        $('#preview').html('');
        if (data.documents.length) {
            var prev = null;
            $.each(data.documents, function() {
                var sort = $(':radio[name="sort"]:checked').val();
                var curr = sort === 'name' ? this[sort].charAt(0) : $.cuteTime(null, new Date(this[sort] * 1000).toString()).replace(/^\w/, function($0) { return $0.toUpperCase(); });
                if (curr !== prev) {
                    $('#results').append('<div style="margin-top: 10px; font-weight: bold;">' + curr + '</div>');
                    prev = curr;
                }
                $('#template').tmpl(this).data('document', this).appendTo('#results');
            });
            selectedID = $('div.type').length === 1 ? $('div.type').attr('id') : selectedID;
            $('#' + selectedID + ' > a').click();
            $('input[name="tag"]').autocomplete({
                source: 'ws/tags',
                select: function(event, ui) {
                    $(this).val(ui.item.value);
                    $(this).parent().submit();
                }
            });
        } else {
            $('#results').html('<div style="margin-top: 10px">No results found</div>');
        }
        $('#filter').html($('#filtertemplate').tmpl(data));
        $('#paging').html($('#pagingtemplate').tmpl(data));
    }});
    $('#q').focusin(function() {
        $(this).select();
    }).autocomplete({
        source: 'ws/autocomplete',
        minLength: 3,
        select: function(event, ui) {
            $(this).val(ui.item.value);
            $(this).trigger('search');
        }
    }).keypress(function(e) {
        if (e.keyCode == 13) {
            $(this).trigger('search');
            return false;
        }
    }).on('search', function() {
        $('#search input[name=page]').val(0);
        $('#search').submit();
    });
    $('#fulltext').ajaxForm({dataType: 'json', success: function(data) {
        if (data.documents.length) {
            $('#fulltext-results').empty();
            $.each(data.documents, function() {
                $('#fulltext-template').tmpl(this).data('document', this).appendTo('#fulltext-results');
            });
        } else {
            $('#fulltext-results').html('<div style="margin-top: 10px">No results found</div>');
        }
    }}).keypress(function(e) {
        if (e.keyCode == 13) {
            $(this).trigger('search');
            return false;
        }
    }).on('search', function() {
        $('#fulltext').submit();
    });
    $('#fulltext-results').delegate('div > a', 'click', function() {
        $('#q').val('id:' + $(this).parent().data('document')._id.$oid);
        $('#search').submit();
        $('#tabs').tabs("select", 0);
    });
    $('#tabs').tabs({
        select: function(event, ui) {
            if (ui.tab.hash === '#tabs-analytics' && $('#stats').is(':empty')) {
                updateAnalytics();
            } else if (ui.tab.hash === '#tabs-files' && $('#files').is(':empty')) {
                $('#files').load('download/index?path=/' + user.id + '/');
            }
        },
        show: function(event, ui) {
            if (ui.tab.hash === '#tabs-upload') {
                $.get('ws/folders', function(folders) {
                    $('#upload-location').empty();
                    _.each(folders, function(folder) {
                        $('#upload-location').append('<option>' + folder);
                    });
                });
                if ($('#html5_uploader').pluploadQueue()) {
                    $('#html5_uploader').pluploadQueue().destroy();
                }
                $('#html5_uploader').pluploadQueue({
                    runtimes: 'html5,flash',
                    url: 'ws/upload',
                    max_file_size: '10mb',
                    flash_swf_url: 'public/javascripts/plupload.flash.swf',
                    unique_names: true,
                    filters: [
                        {title: 'Articles', extensions: 'pdf'},
                        {title: 'Documents', extensions: 'doc,docx,xls,xlsx,ppt,pptx'},
                        {title: 'Scripts', extensions: 'py,m'},
                        {title: 'Images', extensions: 'jpg,gif,png,lif,cxd'},
                        {title: 'Data', extensions: 'cel'}
                    ]
                });
                //following code enables uploader to be reused with reloading
                var uploader = $('#html5_uploader').pluploadQueue();
                uploader.bind('UploadProgress', function() {
                    if (uploader.total.uploaded === uploader.files.length) {
                        $(".plupload_buttons").css("display", "inline");
                        $(".plupload_upload_status").css("display", "inline");
                        $(".plupload_start").addClass("plupload_disabled");
                    }
                });
                uploader.bind('QueueChanged', function() {
                    $(".plupload_start").removeClass("plupload_disabled");
                });
            } else if (ui.tab.hash === '#tabs-data') {
                var i = document.createElement('input');
                if ('autofocus' in i) {
                    $('#q').focus();
                }
            }
        }
    });
    $('ul.ui-tabs-nav').prepend('<li style="font-family: Lobster; margin: 0 0.5em; top: -3px; font-size: 1.5em; font-weight: normal">adam</li>');
    users = _.without(data.users, data.user.id);
    users.sort();
    init(data.user);
    $('#helptemplate').tmpl(data).appendTo('#tabs-help');
    if (user.admin) {
        $('#tabs').tabs('add', '#tabs-analytics', 'Analytics');
        $('#tabs-analytics').show();
    }
    setTimeout(poll, 5000);
    var id = window.location.hash.substring(1);
    if (id) {
        $('#q').val('id:' + id);
    }
    $('#search').submit();
});
