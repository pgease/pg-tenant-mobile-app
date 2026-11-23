package in.pgease.tenant.shell;

/**
 * Small scripts injected into the remote tenant site. The site does not ship a
 * Capacitor bundle, so the shell installs the behaviour the native chrome needs.
 */
public final class ShellScripts {

    private ShellScripts() {}

    /**
     * Tracks whether a pull gesture would fight an inner scroller or an open sheet,
     * and publishes the optional push hook the web app already types.
     */
    public static final String INSTALL = ""
        + "(function(){"
        + "if(window.__pgeaseShellInstalled)return;"
        + "window.__pgeaseShellInstalled=true;"
        + "function visible(el){"
        + "if(!el||el.nodeType!==1)return false;"
        + "var s=getComputedStyle(el);"
        + "if(s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity)===0)return false;"
        + "var r=el.getBoundingClientRect();"
        + "return r.width>8&&r.height>8;"
        + "}"
        + "function overlayOpen(){"
        + "var nodes=document.body?document.body.querySelectorAll('div'):[];"
        + "for(var i=0;i<nodes.length;i++){"
        + "var el=nodes[i];var s=getComputedStyle(el);"
        + "if(s.position!=='fixed'||!visible(el))continue;"
        + "var r=el.getBoundingClientRect();"
        + "if(r.width>=window.innerWidth*0.85&&r.height>=window.innerHeight*0.45)return true;"
        + "}"
        + "return false;"
        + "}"
        + "function canPull(target){"
        + "if(overlayOpen())return false;"
        + "var el=target;"
        + "while(el&&el!==document.documentElement){"
        + "if(el.scrollHeight>el.clientHeight+2&&el.scrollTop>0){"
        + "var oy=getComputedStyle(el).overflowY;"
        + "if(oy==='auto'||oy==='scroll'||oy==='overlay')return false;"
        + "}"
        + "el=el.parentElement;"
        + "}"
        + "var winTop=window.scrollY||document.documentElement.scrollTop||document.body.scrollTop||0;"
        + "return winTop<=0;"
        + "}"
        + "document.addEventListener('touchstart',function(e){"
        + "try{"
        + "if(window.PGEaseShell&&PGEaseShell.setPullEnabled){"
        + "PGEaseShell.setPullEnabled(canPull(e.target));"
        + "}"
        + "}catch(err){}"
        + "},{passive:true,capture:true});"
        + "var bridge=window.PGEaseBridge||(window.PGEaseBridge={});"
        + "if(!bridge.postMessageToNative){bridge.postMessageToNative=function(){};}"
        + "if(!bridge.requestPushPermission){"
        + "bridge.requestPushPermission=function(){return Promise.resolve(null);};"
        + "}"
        + "})();";

    /**
     * Returns true when a sheet was closed, the string "blocked" when a full-screen
     * overlay should swallow back, and false when history should move.
     */
    public static final String CLOSE_OVERLAY = ""
        + "(function(){"
        + "function visible(el){"
        + "if(!el||el.nodeType!==1)return false;"
        + "var s=getComputedStyle(el);"
        + "if(s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity)===0)return false;"
        + "var r=el.getBoundingClientRect();"
        + "return r.width>8&&r.height>8;"
        + "}"
        + "function closeButton(root){"
        + "var labeled=root.querySelector('[aria-label=\"Close\"],[aria-label=\"close\"]');"
        + "if(labeled&&visible(labeled))return labeled;"
        + "var buttons=root.querySelectorAll('button');"
        + "for(var i=0;i<buttons.length;i++){"
        + "var b=buttons[i];"
        + "if(!visible(b))continue;"
        + "var text=(b.innerText||'').replace(/\\s+/g,' ').trim();"
        + "if(text.length>0)continue;"
        + "if(!b.querySelector('svg'))continue;"
        + "var r=b.getBoundingClientRect();"
        + "var o=root.getBoundingClientRect();"
        + "if(r.width<=72&&r.height<=72&&r.top<o.top+160)return b;"
        + "}"
        + "return null;"
        + "}"
        + "var nodes=document.body?document.body.querySelectorAll('div'):[];"
        + "var best=null,bestArea=0;"
        + "for(var i=0;i<nodes.length;i++){"
        + "var el=nodes[i];var s=getComputedStyle(el);"
        + "if(s.position!=='fixed'||!visible(el))continue;"
        + "var r=el.getBoundingClientRect();"
        + "if(r.width<window.innerWidth*0.85||r.height<window.innerHeight*0.45)continue;"
        + "var area=r.width*r.height;"
        + "if(area>=bestArea){best=el;bestArea=area;}"
        + "}"
        + "if(!best)return false;"
        + "if(window.PGEaseBridge&&typeof window.PGEaseBridge.onNativeBack==='function'){"
        + "try{if(window.PGEaseBridge.onNativeBack())return true;}catch(err){}"
        + "}"
        + "var btn=closeButton(best);"
        + "if(btn){btn.click();return true;}"
        + "var buttons=best.querySelectorAll('button');"
        + "for(var j=0;j<buttons.length;j++){"
        + "if(/retry/i.test(buttons[j].innerText||''))return 'blocked';"
        + "}"
        + "return false;"
        + "})();";
}
