function ReviewImage(){
var image=["assets/reviewimage.webp","assets/reviewimage2.webp","assets/reviewimage3.webp"];
var index=0;
var slider=document.getElementById("slider");
slider.addEventListener("click",function(){
    index=(index+1)%image.length;
    slider.src=image[index];
});
}
function CountTime(){
     let endDate=new Date("Dec 31, 2026 23:59:59").getTime();
    let x=setInterval(function(){
        let now=new Date().getTime();
        let distance=endDate-now;
        let days=Math.floor(distance/(1000*60*60*24));
        let hours=Math.floor((distance%(1000*60*60*24))/(1000*60*60));
        let minutes=Math.floor((distance%(1000*60*60))/(1000*60));
        let seconds=Math.floor((distance%(1000*60))/1000);
        document.getElementById("counttime").innerHTML="Khuyến mãi còn "+
        days+" Ngày "+hours+" Giờ "+minutes+" Phút "+seconds+" Giây ";
        if(distance<0){
        clearInterval(x);
        document.getElementById("counttime").innerHTML="hết hạn";
    }
    },1000)
}
function ShowDesc(){
    //var img=["assets/acer_shadow_desc_img2.png","assets/acer_shadow_desc_img3.png","assets/acer_sadow_desc_img4.png","assets/acer_sadow_desc_img5.png"]
    var DescImg=document.getElementById("desc_img_0");
    var DescImg1=document.getElementById("desc_img_1");
    var DescImg2=document.getElementById("desc_img_2");
    var DescImg3=document.getElementById("desc_img_3");
    var DescImg4=document.getElementById("desc_img_4");
    DescImg1.addEventListener("click",function(){
        DescImg.src="assets/acer_shadow_desc_img2.png";
    });
    DescImg2.addEventListener("click",function(){
        DescImg.src="assets/acer_shadow_desc_img3.png";
    });
        DescImg3.addEventListener("click",function(){
        DescImg.src="assets/acer_sadow_desc_img4.png";
    });
        DescImg4.addEventListener("click",function(){
        DescImg.src="assets/acer_sadow_desc_img5.png";
    });
}
function init(){
    ReviewImage();
    CountTime();
    ShowDesc();
}
window.onload=init;
