let restartingApp=false;
async function restartApplication(){
 if(restartingApp)return;
 restartingApp=true;
 document.querySelectorAll('[data-action="app-restart"]').forEach(b=>{b.disabled=true;b.textContent='正在重新启动…';});
 const result=await call('app-restart');
 if(!result){restartingApp=false;render();}
}
document.addEventListener('click',e=>{if(e.target.closest('[data-action="app-restart"]'))restartApplication();});
document.addEventListener('keydown',e=>{if(e.ctrlKey&&e.shiftKey&&e.key.toLowerCase()==='r'){e.preventDefault();restartApplication();}});
